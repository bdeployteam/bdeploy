[![Build Status](https://travis-ci.com/bdeployteam/bdeploy.svg?branch=master)](https://travis-ci.com/bdeployteam/bdeploy)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?metric=alert_status&project=bdeployteam_bdeploy)](https://sonarcloud.io/dashboard?id=bdeployteam_bdeploy)
[![Test Coverage](https://sonarcloud.io/api/project_badges/measure?metric=coverage&project=bdeployteam_bdeploy)](https://sonarcloud.io/dashboard?id=bdeployteam_bdeploy)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?metric=sqale_rating&project=bdeployteam_bdeploy)](https://sonarcloud.io/dashboard?id=bdeployteam_bdeploy)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?metric=reliability_rating&project=bdeployteam_bdeploy)](https://sonarcloud.io/dashboard?id=bdeployteam_bdeploy)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?metric=security_rating&project=bdeployteam_bdeploy)](https://sonarcloud.io/dashboard?id=bdeployteam_bdeploy)

# What do we need?

Some products require from their deployment solution :

* A stable handling of the initial deployment of a huge product 
* Short turnarounds on the frequent updates e.g. during commissioning that have only tiny deltas or configuration changes
* Free composition of which process should live on which node and management of the processes at runtime (start / stop / keep-alive the processes)
* The functionality of providing a true overview of the products out there in the wilderness.
* The ability to configure and deploy not only server applications but desktop clients as well that share their configuration with the corresponding server applications

# Deployment systems on the market

Existing systems either:

* Deploy a single (possibly large) application that don't update often.
  * Mostly a single "entry point" (service, manual started application, ...)
  * Updates happen only from time to time, so they are "allowed" to be slow and manual
* Are cloud focused and deploy many (but small) applications that update frequently
  * Using a hosting system like e.g. Kubernetes

# BDeploy is different

BDeploy aims at solving some (if not all) of these issues.

* Provides file-hash based delta updates through the self-invented BHive technology (Git-like hash-tree based file content management). 
* Basically every update (application, configuration, etc.) is a "delta" update. The initial installation just has a relatively huge delta.
* Provides flexible configuration of process compositions (both server and client).
* Provides multi-target-node setup possibilities even for classic on premise deployments (i.e. without support of cluster software).
* Provides rich configuration possibilities for the applications deployed. Each application can define it's own set of parameters known to it.
* Provides mechanisms to synchronize configuration between connected processes (e.g. server and client configuration).
* Provides a distribution mechanism which extends delta updating down to the desktop client using a dedicated launcher.
* All of this make BDeploy suitable for deploying highly dynamic products with extreme performance and flexibility demands.

# A thought on the cloud
The cloud (and clusters) is unquestionably a thing that is not going to go away any time soon. Yet not all applications are ready to be deployed in cloud environments. Also, even with current tooling, not every setup (especially in restricted environments) is feasible.

Even today, most tools related to "cloud" and "clusters" (e.g. "kubectl", "helm", etc.) are thinking about the cloud as a single thing. Some restricted environments would require to have a "cluster per installation" (e.g. per customer's restricted network). In such environments, orchestration and management of the plethora of clusters is giving devops teams headaches. Also preparing and writing configuration for applications which then can be targeted at different cluster setups is everything but easy/simple.

BDeploy is built in a way that it can act as bridge to such systems (e.g. Kubernetes) in the future (support pending). It understands most of the concepts behind such cluster solutions and can be used to manage "clusters" instead of physical machines. This not only creates a tooling to manage clusters, but also allows to create hybrid solutions where a single setup is composed of a single configuration spanning physical, virtual, clustered and client nodes.

