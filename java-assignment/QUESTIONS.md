# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```
There are three patterns in use in this repository.

Store uses Panache's Active Record
Product uses a Repository pattern
Warehouse uses a hexagonal architecture with domain ports and usecases.

For business heavy domains like warehouses, it makes sense to use the hexagonal architecture. For products which is alright with simple CRUD such a refactor is probably overkill. I would instead only refactor the Store and Product areas if and only if we have further changes that involve more business rules.

I know that this is a assignment so the locations are hardcoded, but I would also consider some kind of external store for the locations as well. This would allow for expansion without having to change the config here.

```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```
I think the OpenAPI specifications need to exist for all endpoints.

The pros of defining the OpenAPI specification is that we ensure that consumers of the API have a well defined API interface and it also makes it easier to generate clients by use of the yaml file. The Swagger documentation also becomes better defined with what callers can expect in both success and failure conditions.

The cons are additional build tooling, generated code maintanance and the need to inform and coordinate with the consumers of the API which can slow down builds and delivery but it is very necessary to avoid mismatches in the expectations between two services. Care should be taken for backwards compatability and API versioning instead.

```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```
I would prioritize tests according to business risk. We need fast unit tests for the warehouse usecases with both positive and negative cases.

Next would be repository integration tests so that we can test the database used in a beta environment mirroring the actual production environment. Transaction behaviour would be the highest priority.

Coverage should be treated as a diagnostic instead of a fixed threshold since when we make a metric tied to outcomes, we tend to treat the metric itself as a goal. Policy can be used to maintain coverage standards and we should implement CI checks for coverage on changed/added code as part of the MR template.

```
