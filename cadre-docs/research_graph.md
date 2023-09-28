# Research Graph (RG) - Documentation

## Research Graph

### User's research graph details - Filtering

The research graph JSON of the user is huge and complex.
The JSON basically has 3 Objects, namely:
1) nodes
2) relationships
3) stats

The RG nodes, int turn, contain 5 kinds of details mentioned below (each one of them is JSON Array):
1) datasets
2) grants
3) organisations
4) publications
5) researchers 

The user might directly or in-directly is associated with the nodes.
And each JSON Object within the respective JSON Array, has a key.
hence, to fetch the nodes details which are directly associated to the user, we first need to know the list of key.

We can fetch this list of keys by iterating the required relationships (researcher-dataset, researcher-grant, researcher-organisation, researcher-publication, researcher-researcher), where the "from" key matches the ORCiD of the user, and the respetive "to" field value would be the key we need.

#### API

Below is the CADRE API to fetch the RG details (JSON) of the user.

*/api/research-graph/get-user-full-details*

The API accepts to input query parameters, namely:
* ORCiD (Mandatory)
* input-node (Optional - Values: datasets, grants, organisations, publications, 
researchers)
* filter-node (Optional, Values: true/false, default: false)

This API is currentl only available to the CADRE Admins.
If the "input-node" is not specified in query paramter of the API invocation, then the API response would contain of the RG nodes of the user, whose ORCiD matches with the ORCiD provided in the input query parameter.

If the "input-node" field is specified with the valid input value, then the API response would only contain the respective RG nodes of the user, based on the input specified.

If optional "filter-node" is selected to *true*, then the specified RG Node will be filtered to fetch the detials to which the user is directly associated. 

### Interface URL:
Research Graph has a beautiful interface to view the RG of the user.
Below is the URL to the UI, replace "XXXX-XXXX-XXXX-XXXX" with valid ORCiD.

*URL Format:*
https://researchgraph.com/orcid/XXXX-XXXX-XXXX-XXXX/

## Augment API

#### GitHub Link's:
https://github.com/researchgraph/augment-api-beta

https://github.com/researchgraph/augment-api-beta/blob/main/docs/notebooks/orcid.ipynb

#### APIs:

Below the the RG REST GET API to fetch the RG detials of the user, based on ORCiD. We would need to also input the valid subscription-key (API Key).
The subscription-key can be obtained from the REMS configuration file.

*Syntax:*

https://augmentapi.researchgraph.com/v1/orcid/0000-0002-4259-9774?subscription-key=xxxxxxxxxxxxxxxxxxxxxxx