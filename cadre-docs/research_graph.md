# Research Graph (RG) - Documentation

## Research Graph

### User's research graph details - Filtering

The research graph JSON of the user is huge and complex.
The JSON Array basically has 3 Objects, namely:
1) nodes
2) relationships
3) stats

The RG "nodes" object, in-turn, contain 5 kinds of details mentioned below (each one of them is JSON Array):
1) datasets
2) grants
3) organisations
4) publications
5) researchers 

The user might directly or in-directly is associated with the nodes.
And each JSON Object within the respective JSON Array (Ex. datasets), has a key.

*Example JSON Array of "datasets" node:*
```
[
  {
    "nodes": {
      "datasets": [
        {
          "authors_list": "Amir Aryani",
          "datacite_type": "dataset",
          "doi": "xx.xxxx/xx.xxxxxxxx.xxxxxxx.xx",
          "key": "researchgraph.com/doi/xx.xxxx/xx.xxxxxxxx.xxxxxxx.xx",
          "publication_year": "2023",
          "r_number": "xxxxxxxxx",
          "title": "Example title - 1"
        },
        {
          "authors_list": "vikas chinchansur",
          "datacite_type": "dataset",
          "doi": "yy.yyyy/yy.yyyyyyyy.yyyyyyy.yy",
          "key": "researchgraph.com/doi/yy.yyyy/yy.yyyyyyyy.yyyyyyy.yy",
          "publication_year": "2020",
          "r_number": "yyyyyyyyy",
          "title": "Example title - 2"
        }
      ]
    }
  }
]
```

Hence, to fetch the "nodes" details which are directly associated to the user, we first need to know the list of key.

We can fetch this list of keys by iterating the required relationships (researcher-dataset, researcher-grant, researcher-organisation, researcher-publication, researcher-researcher), where the "from" key matches the ORCiD of the user, and the respetive "to" field value would be the key we need.

#### API

Below is the CADRE API to fetch the RG details (JSON) of the user.

##### *Path:*

/api/research-graph/get-user-full-details

##### *URL:*

https://admin.test.cadre.ada.edu.au/swagger-ui/index.html#/researchgraph/post_api_research_graph_get_user_full_details

##### *Headers:*

Since the API is "POST" method, its must to specify the headers in the API invocation.
* x-rems-api-key
* x-rems-user-id

##### *Query Parameters:*

The API accepts three input query parameters, namely:
* orcid (Mandatory)
* node (Optional - Values: datasets, grants, organisations, publications, 
researchers)
* filter-node (Optional, Values: true/false, default: false)

This API is currently only available to the CADRE Admins.
If the "node" query parameter is not specified in the API invocation, then the API response would be the entire RG JSON, whose ORCiD matches with the *orcid* provided in the input query parameter. If the specified *orcid* doesn't belong to any of the CADRE users, then a "404 - Not Found" error is thrown by the API.

If the *node* query parameter is specified with the valid input value, then the API response would only contain the respective RG node of the user, based on the input specified.
*Note:* This would return the entire node as it is, without having filtered the specified node to fetch only the details associted to the user! For example, if the value provided for the *node* query parameter is "datasets", then the API would return the "datasets" node and would trim off all the other nodes in the response.

If optional "filter-node" is selected to *true*, then the specified RG Node will be filtered to fetch only the details to which the user is directly associated. 

### Interface URL:
Official Research Graph website has a beautiful interface to view the RG of the user.
Below is the URL to the UI, replace "XXXX-XXXX-XXXX-XXXX" with valid ORCiD.

*URL Format:*
https://researchgraph.com/orcid/XXXX-XXXX-XXXX-XXXX/

## Augment API

Research Graph has *Augment APIs* to fetch the RG of the user in a JSON format.
More details on the Augment APIs can be found at:
https://researchgraph.org/augment-api/


#### GitHub Link's:
Below are the GitHub links to the Augment APIs.
https://github.com/researchgraph/augment-api-beta

https://github.com/researchgraph/augment-api-beta/blob/main/docs/notebooks/orcid.ipynb

##### APIs:

Below the the RG REST GET API to fetch the RG detials of the user, based on ORCiD. We would need to also input the valid subscription-key (API Key).
The subscription-key can be obtained from the REMS configuration file.

*Syntax:*

https://augmentapi.researchgraph.com/v1/orcid/0000-0002-4259-9774?subscription-key=xxxxxxxxxxxxxxxxxxxxxxx