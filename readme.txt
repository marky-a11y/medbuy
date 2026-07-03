The purpose of this application to to build a analyst/strategist dashboard.
This dashboard is a focused view of information that would be relevent to new engagements of media buying.

The features of the application are:
Provide a suggestion of the top ad platform + commerce sector combination.
Within this suggestion of a top ad platform + commerce sector, a list of suggestions for top company clients is given.
This list of clients isn't a single suggestion - if only a single suggestion is provided and then obtaining that company as a client falls through, there wouldn't be any opportunity secured.  
It's better to provide multiple suggestions so that they can all be investigated in parallel.

In addition to suggestions focused on a top ad platform + commerce sector combination, there are also suggestions for filling gaps in the portfolios.
If the portfolios don't have exposure to a commerce sector and that commerce sector is hot, the AI will suggest why adding a client within that sector would be beneficial.

In addition to direct suggestions of clients, there is also a ROI estimate calculator.
The first supporting evidence for vetting a suggestion would be clicking the additional information icon next to the suggestion to see the KPI metrics.
The next step would be to plug in that suggestion into the ROI calculator to see potential profit in a specific advertisement opportunity.
The final step would be a future enhancement to start the inquiry/contract process for engaging with the client (integration to an existing engagement process).


Note: This dashboard features a lot of mock data as some of the APIs to fetch real data are paid-tier only or are complex.

Future State:
- Fetch data from all API sources to feature full live real-time data
- Update aggregation methods and cross-reference to verify the aggregation is working correctly
- Finalize KPI metrics for ad platform + commerce sector, ad platform + commerce sector + company, company, and ad platform levels.
- Finalize overall score calculation to be LLM-driven (or machine learning?) to produce better overall score and better suggestions
- Update the ROI Calculation form to be more user friendly (showing all information all of the time on a single form to easily cross-reference the data)
- Portfolio coming from company web service instead of mock data
- Automated process start of acquiring a new client through an integration
- Notes feature for portfolios
- Save suggestions, Save ROI calculation to file/email
- Provide suggestions for current portfolio for additional/new advertising.
- additional user roles/permissioning