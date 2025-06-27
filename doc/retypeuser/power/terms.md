---
order: 5
icon: info
---

<style>
    .t1 td {
        vertical-align: text-top;
    }
    .t1 th:first-child {
        width: 25%;
    }
</style>

# Advanced Terms

Term | Meaning
--- | ---
**BHive** | The actual storage backend. Provides data de-duplication and delta-based transfers. Synchronizing two **BHives** will transfer just the files that are not present in the other (potentially remote) hive.
**Manifest** | A **Manifest** is a unique **Key** which identifies a **Tree** of **Files** inside a **BHive**.<br/><br/>A **Key** always has the form `name:tag`. Some CLI commands expect such a key as input. If that is the case, the correct aforementioned syntax must be followed.
**Software Repository** | A **Software Repository** is a named **BHive** which is dedicated to storing **Manifests** which contain arbitrary _deliverables_. This is not necessarily a piece of software. It may be _any_ set of files and directories.<br/><br/>When building a **Product**, its declared dependencies are resolved by querying all **Software Repositories** on the target **BDeploy** server.<br/><br/>In addition to arbitrary software, **Software Repositories** can also host **Products**, just like **Instance Groups**. The purpose of this feature is to provide a shared place which can be used to provide **Products** to multiple **Instance Groups**.
