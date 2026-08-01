# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**

Questions:

What kind of decisions are the cost modeling enabling?

What kind of sources do we get the cost tracking information from?

Who owns the cost definition at each level? What will the process to update cost definitions look like?

What is the reporting granularity? Based on the answer (product level, shop level, warehouse level), the volume of data will be different and will require different approaches to handle it.

What challenges do we face in terms of compliance and auditing? How long a window for eventual consistency is acceptable?

I've worked on seller fees in the past and found that updating a common ledger based on events that occur at different stages with different implications on income and expenditure requires specific handling. Costs being broken down and allocated on the stages of sourcing the product (inventory), labor, transportation will likely not be entered into the ledger all at once. This likely means we will need to handle sourcing of information from multiple systems and then reconcile them.

## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**
Cost optimization is a constrained optimization problem. Questions I would have before beginning any sort of strategy:

1. Compliance: safety, contractual or labor constraints
2. What is the biggest source of costs? The peter principal suggests 80% of the costs would come from 20% of the cost drivers so I would first focus on those.
3. What service metrics are most important? This is needed to make payoffs so that we can understand which metrics can be allowed to detoriate to reduce costs
4. Can demand be predicted? Seasonality and trends can help prevent over production and minimize overhead costs and excess.

Once we have a strategy, we need to consider ease of rollback and pilot by running at a smaller scale. My experience at Tavant taught me that running reliable experimentation is key to understanding the impact of this kind of optimization. We need to identify a baseline and use a control group to measure the impact against the pilot group.

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**
1. What does real time refer to? Financial data reported might be available, but accounting entries on a ledger will be subject to validation which will mean that the data is not truly "real time"
2. How do we ensure that costs are truly controlled? Will this be an alerting system or will it contain logic that prevents costs from being realized?
3. What is the comfortable threshold and tolerance levels for the cost control system?

The company benefits from having a cost control system as it provides visiblity and allows for better decision making while minimizing manual entry.

For seamless integration, it makes sense to use queues and event based architecture rather than synchronous calls. There should be handling for duplicate events, out of order events and missing events. Multiple layers of reconcilation are required.

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**
1. What is the granularity of the forecast? Weekly, monthly, yearly?
2. Who will be using the forecasts to make decisions?
3. Who will be defining the budgets?
4. What kind of decisions will be made using the forecasts?

Forecasting allows the company to anticipate demand, labor, inventory, transport and facility costs beforehand. This allows the company to reduce costs in overhead and excess. Identifying seasonality in these costs allows us to identify risk earlier.

Forecasting is best done by defining a model based on previous data. An example of this can be machine learning based systems. This system will need data management, from accurate record keeping, identifying parameters needed for prediction (think of algorithms like PCA).

We can further break the problem down by identifying major cost drivers and using the algorithm to forecast at this granular level and then use that to identify the right budget.

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**
While the same business unit code will be preserved, it is important to make a distinction between the cost history of the old warehouse versus the new one. Treating the old cost history as archived data allows us to make meaningful comparisions between the old and new warehouses. The seperation is needed to avoid old data appearing as if belonging to the new warehouse.

I would want to know whether old warehouses can be reopened, and if there is a window where both warehouses are functioning as a transition period.

This is especially important for the Fulfilment constraints where there are a limit to the number of products per store per warehouse, and number of warehouses per store.

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.
