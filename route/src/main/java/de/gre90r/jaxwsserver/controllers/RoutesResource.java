package de.gre90r.jaxwsserver.controllers;

import de.gre90r.jaxwsserver.exception.RouteNotFoundException;
import de.gre90r.jaxwsserver.model.Route;
import de.gre90r.jaxwsserver.service.RouteService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

@Path("/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class RoutesResource {

    @PersistenceContext(unitName = "RoutesPU")
    private EntityManager em;

    @Inject
    private RouteService routeService;

    // Обработчик исключений валидации
    @Provider
    public static class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
        @Override
        public Response toResponse(ConstraintViolationException exception) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<?> cv : exception.getConstraintViolations()) {
                sb.append(cv.getPropertyPath()).append(": ").append(cv.getMessage()).append("\n");
            }
            return Response.status(Response.Status.BAD_REQUEST).entity(sb.toString()).build();
        }
    }

    @Context
    private HttpServletRequest request;

    @GET
    public Response getAllRoutes(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("order") @DefaultValue("asc") String order,
            @QueryParam("name") String nameFilter,
            @QueryParam("fromLocationId") Long fromLocationId,
            @QueryParam("toLocationId") Long toLocationId,
            @QueryParam("minDistance") Double minDistance,
            @QueryParam("maxDistance") Double maxDistance) {

        checkCert();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Route> cq = cb.createQuery(Route.class);
        Root<Route> root = cq.from(Route.class);

        List<Predicate> predicates = new ArrayList<>();

        // Фильтр по имени
        if (nameFilter != null && !nameFilter.isEmpty()) {
            predicates.add(cb.like(root.get("name"), "%" + nameFilter + "%"));
        }

        // Фильтр по fromLocationId
        if (fromLocationId != null) {
            predicates.add(cb.equal(root.get("from").get("id"), fromLocationId));
        }

        // Фильтр по toLocationId
        if (toLocationId != null) {
            predicates.add(cb.equal(root.get("to").get("id"), toLocationId));
        }

        // Фильтр по минимальному расстоянию
        if (minDistance != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("distance"), minDistance));
        }

        // Фильтр по максимальному расстоянию
        if (maxDistance != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("distance"), maxDistance));
        }

        // Применение фильтров
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        // Применение сортировки
        if ("desc".equalsIgnoreCase(order)) {
            cq.orderBy(cb.desc(root.get(sort)));
        } else {
            cq.orderBy(cb.asc(root.get(sort)));
        }

        TypedQuery<Route> query = em.createQuery(cq);

        // Пагинация
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);

        List<Route> routes = query.getResultList();

        GenericEntity<List<Route>> entity = new GenericEntity<>(routes) {};
        return Response.ok(entity).build();
    }

    /**
     * Добавление нового маршрута.
     *
     * @param route   маршрут для добавления
     * @param uriInfo информация о URI
     * @return ответ с созданным маршрутом
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addRoute(@Valid Route route, @Context UriInfo uriInfo) {
        checkCert();
        Route createdRoute = routeService.addRoute(route);

        UriBuilder builder = uriInfo.getAbsolutePathBuilder();
        builder.path(Long.toString(createdRoute.getId()));
        return Response.created(builder.build()).entity(createdRoute).build();
    }

    // Получить маршрут по ID
    @Path("/{id}")
    @GET
    public Response getRouteById(@PathParam("id") long id) {
        checkCert();
        Route route = em.find(Route.class, id);
        if (route == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Route not found").build();
        }
        return Response.ok(route).build();
    }

    /**
     * Обновление существующего маршрута.
     *
     * @param id           идентификатор маршрута для обновления
     * @param updatedRoute обновленные данные маршрута
     * @return ответ с обновленным маршрутом или ошибкой
     */
    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateRoute(@PathParam("id") long id, @Valid Route updatedRoute) {
        checkCert();
        try {
            Route route = routeService.updateRoute(id, updatedRoute);
            return Response.ok(route).build();
        } catch (RouteNotFoundException ex) {
            return Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build();
        }
    }

    // Удалить маршрут
    @Path("/{id}")
    @DELETE
    @Transactional
    public Response deleteRoute(@PathParam("id") long id) {
        checkCert();
        Route route = em.find(Route.class, id);
        if (route == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("Route not found").build();
        }
        em.remove(route);
        return Response.noContent().build();
    }

    // Получить маршрут с максимальным значением 'from'
    @Path("/from/max")
    @GET
    public Response getRouteWithMaxFrom() {
        checkCert();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Route> cq = cb.createQuery(Route.class);
        Root<Route> root = cq.from(Route.class);

        // Проверяем, что поле 'from' не null
        cq.where(cb.isNotNull(root.get("from")));

        // Вычисляем сумму x + y + z для 'from'
        Expression<Integer> sumExpr = cb.sum(
                cb.sum(
                        cb.coalesce(root.get("from").get("x"), 0),
                        cb.coalesce(root.get("from").get("y"), 0)
                ),
                cb.coalesce(root.get("from").get("z"), 0)
        );

        // Сортируем по сумме в порядке убывания
        cq.orderBy(cb.desc(sumExpr));

        TypedQuery<Route> query = em.createQuery(cq);
        query.setMaxResults(1);

        List<Route> result = query.getResultList();
        if (result.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity("No routes found").build();
        }
        return Response.ok(result.get(0)).build();
    }

    // Получить количество маршрутов с расстоянием меньше заданного
    @Path("/distance/lower/{value}/count")
    @GET
    public Response getCountOfRoutesWithDistanceLowerThan(@PathParam("value") double value) {
        checkCert();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Route> root = cq.from(Route.class);

        cq.select(cb.count(root));
        cq.where(cb.lessThan(root.get("distance"), value));

        Long count = em.createQuery(cq).getSingleResult();

        return Response.ok("{\"count\":" + count + "}").build();
    }

    private void checkCert(){
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
        if (certs != null && certs.length > 0) {
            X509Certificate clientCert = certs[0];
            // Проверка надежности
        }
    }
}
