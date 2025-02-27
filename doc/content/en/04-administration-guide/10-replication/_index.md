---
title: Replication
weight: 10
url: /administration/replication
aliases: [ /tutorial/admin/replication ]
description: This page contains details about how to create different cluster arquitecture topologies by using the replication features.
---

StackGres supports all Postgres and Patroni features to set the different replication options that come with these technologies. Indeed, StackGres don't use any custom owned replication mechanism or protocol, it fully relies upon the official Postgres replication development. Furthermore, StackGres relies upon the Patroni HA development, therefore, failOver, switchOver, and replication should work as any other Postgres cluster.

{{% children style="li" depth="1" description="true" %}}