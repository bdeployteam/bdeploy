---
order: 2
icon: key
---

# System Overview

## 1. Introduction – What is BDeploy?

**BDeploy** is an open-source deployment platform designed to manage the delivery of software products across distributed systems. It provides a centralized interface for managing releases, configurations, and deployments of both server and desktop client applications. BDeploy is particularly well-suited for environments with multiple target systems such as testing, staging, and production. It ensures reproducible and efficient deployments at any time.

## 2. Core concepts and terminology

BDeploy introduces a set of core concepts that structure the deployment lifecycle. Below are key [terms](https://bdeploy.io/user/intro/terms/index.html) in short:

- **Product**: A piece of software that can be deployed with BDeploy.
- **Product version**: One specific version - one release - of a product.
- **Instance**: A deployment configuration of a product.
- **Instance version**: One immutable version of the configuration of an instance.
- **Instance group**: A logical grouping of instances for a project or customer.
- **Application**: An artifact of a product that is either a server process or a desktop client application.
- **Process**: A specific configuration of an application that is started server-side as an OS process.
- **System**: A group of instances across products inside one instance group that serve a common purpose. 
- **Managed server**: A deployment target that can manage one or multiple nodes.
- **Node**: A local agent process on a physical or virtual machine.
- **Launcher**: A client-side tool to start and update desktop applications. 

## 3. Key advantages of BDeploy

- **Efficient data storage**: BDeploy's internal BHive data structure enables data de-duplication and pooling mechanisms. Merkle trees ensure consistency and minimize redundancy.
- **High performance data transfer**: To ensure efficient updates, delta-based data transfer reduces the volume of data transmitted to each node within a deployment target.
- **Decentralized control**: Full local control is ensured, as configuration is stored locally; the central server acts only as auxiliary remote control.
- **Versioned configuration**: Supports reproducible deployments and comprehensive traceability throughout the lifecycle of an instance.
- **Automation**: BDeploy offers REST APIs, build tool integrations, a command-line interface (CLI) and YAML-based configuration. This collectively supports automation across CI/CD pipelines and DevOps workflows, e.g. with Jenkins or Ansible.
- **Runtime control**: BDeploy orchestrates the startup of configured processes in the correct order and continuously monitors their runtime.
- **Monitoring & system health**: Supports troubleshooting and application monitoring with seamless access to log files, data files, process output & process health through the central or local web UI or CLI.
- **Scalability**: Supports both small, standalone setups on a single machine and large-scale environments with numerous nodes, systems, and products.

## 4. High-level overview / solution strategy

![System Overview](/images/systemoverview.png){width=480}

The diagram above presents a high-level overview of the core concepts of BDeploy. It outlines the complete process of configuring and deploying a product to a target system, demonstrating how BDeploy achieves the advantages previously described. The numbered circles in the diagram correspond to the following key concepts:

### 1. Releasing a software product

A release - which is a product version in BDeploy - is an immutable, versioned deployment unit of a software product. It encapsulates all files, resources, scripts, and metadata that are independent of a specific deployment target.

BDeploy does not manage the build process itself. Instead, it takes over the build artifacts and stores them either in a software repository or within an instance group, from where they can be deployed.

Upon importing a product version into BDeploy, all files are hashed and organized into a Merkle tree structure. This structure forms the foundation for efficient delta-based transfers and data de-duplication, enabling optimized deployment performance and storage efficiency.

---

### 2. Software repositories

Products can be stored in software repositories, which serve as central source for all product versions. From these repositories, product versions can be efficiently transferred to instance groups for deployment to specific projects or customers. BDeploy uses the internal BHive format for these software repositories, enabling structured, versioned, and deduplicated storage. 

---

### 3. Central management via BDeploy central server

The BDeploy central server provides a unified view across all attached deployment targets (managed servers) within an instance group, enabling software vendors and integrators to effectively support their customers throughout the entire product lifecycle. Using the web UI or REST API, users can deploy software, manage configurations, and monitor instance states.

Recognizing the importance of security and data privacy, the central server enforces strict user management to ensure that only authorized users can access specific software repositories or instance groups.

To maintain full decentralized control, all configuration data remains local to the deployment target. The central server acts solely as a remote control and visualization layer. This architecture enhances system resilience by allowing fully autonomous operation — even in isolated environments — and ensures continued operation in the rare case of central server outages.

---

### 4. Instance groups

An instance group is the primary top-level structure in BDeploy. It serves as a logical container for instances related to a specific project or customer. This allows users to manage different environments — such as QA, staging, and production — independently and in parallel. This structure enables fine-grained separation of responsibilities and permissions. Instance groups are fully self-contained, as their internal BHive data structure includes everything required for complete operation. This design ensures consistent and efficient data transfer to deployment targets.

---

### 5. Instances - configuration & versioning

An instance in BDeploy represents the deployment of a product using a specific configuration, tailored for a particular purpose. Within an instance, individual processes can be distributed across one or more nodes to enable flexible deployment scenarios. 

BDeploy allows the same product version to be deployed as multiple, independent instances — each with its own configuration — enabling it to serve different purposes or customer environments. This clear separation between the product version (as a software release) and the instance version (which manages deployment-specific configuration) reinforces a strong separation of concerns between development and operations.

---

### 6. Systems for grouping related instances

Within an instance group, a system serves as a logical grouping of multiple instances that work together to fulfill a shared purpose — such as several products jointly operating a production environment. Systems help users structure and manage complex deployment scenarios within heterogeneous instance groups in a focused and organized manner. They also enable the use of shared configuration through system variables, which can be referenced by individual instance configurations. This approach simplifies configuration management and promotes consistency across related deployments. 

---

### 7. Deployment & efficient data transfer

BDeploy uses a delta-transfer mechanism to minimize the amount of data transmitted across the network during deployments. Only the necessary files are transferred — and only if they have changed — reducing bandwidth usage, deployment times, and risk of errors. A key differentiator of BDeploy is the combination of Merkle tree–based file organization, deduplication, and delta-transfer, which enables highly efficient synchronization, even over slow or unstable networks. 

From a process perspective, BDeploy separates the installation of a new instance version (which includes data transfer and file system operations on all target nodes) from the activation and startup of that version (which affects runtime operations). This separation helps minimize potential downtime during deployments and supports smooth transitions to new versions.

---

### 8. Managed servers as deployment targets

In BDeploy, managed servers are the actual deployment targets where instances are operated. They are always integrated to a BDeploy central server via a REST API. To support fully decentralized control, managed servers can operate independently at any time. For this purpose, each managed server provides a local web UI. Access to data and operations on a managed server is restricted through its own user management system, ensuring that only authorized users can interact with the configured instances. This local user management can be integrated with project- or customer-specific directory services, such as LDAP, Active Directory or OIDC.

A managed server can control multiple BDeploy nodes to support deployments that span several physical or virtual machines. Each BDeploy system resides entirely within a single managed server.

---

### 9. BDeploy nodes as local agents

A node is the local execution agent running on a physical or virtual machine. It performs various tasks on the local system. 

First, it handles file system operations by materializing data from the internal BDeploy BHive structure into directories, with a separate directory for each instance version. It also manages local access to log files, data files, and configuration files.

Secondly, the node takes care of starting, stopping, monitoring, and updating instances. To achieve this, a BDeploy node is typically registered as an operating system service that starts automatically. Upon startup, the node ensures that all configured processes are launched automatically. A single node can manage processes for multiple instances, as illustrated in the diagram above. It can start processes sequentially or in parallel according to the configuration of process control groups in BDeploy.

Thirdly, the node handles all local communication with the configured processes, providing BDeploy-specific functionality such as REST endpoints for startup and liveness probes to monitor process health. Communication in BDeploy is strictly unidirectional: the central server communicates with the managed server, which then communicates with the nodes.

This architecture enables fully controlled deployments across distributed target systems, eliminating the need for manual intervention on each physical or virtual machine.

---

### 10. Deployment of desktop client applications

In addition to managing server-side processes, BDeploy also facilitates the deployment of client applications on desktop systems. Through the BDeploy Launcher, users can start applications that are automatically updated and consistently configured, ensuring version alignment between server and client components. By sharing configuration data between server processes and client applications, BDeploy helps prevent unexpected errors caused by misconfigurations. This integrated approach provides BDeploy unique advantages over traditional MDM systems when deploying client applications.

The Launcher is platform-independent and fully compatible with Citrix and terminal server environments, making it suitable for a wide range of enterprise deployment scenarios.

## 5. Conclusion and further resources

BDeploy offers a robust, scalable, and efficient solution for managing software deployments across distributed systems. Its architecture supports both centralized control and decentralized execution, making it ideal for modern DevOps environments.
