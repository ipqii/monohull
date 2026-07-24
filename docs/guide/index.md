# Concepts & navigation

This guide covers day-to-day use of **Monohull**, the automated development
environment manager for IBM Maximo®: how to stand up Maximo environments, run
actions and pipelines against them, wire up per-pull-request builds, and use
the integration-testing helpers.

It assumes Monohull is already installed and reachable in a browser. For getting a
Monohull instance running, see the **[installation guide](../install.md)**; for the
"why", see the **[overview](../index.md)**.

---

## Core concepts

A few terms recur throughout Monohull. Understanding them makes everything else
click into place.

| Concept | What it is |
|---|---|
| **Environment** | A running (or stopped) Maximo instance Monohull provisioned for you: a set of Docker containers on an isolated network, plus its build history and config. This is the thing you *use*. |
| **Container role** | Every environment has containers with fixed roles: **DB** (DB2 or Oracle), **APP** (the WebSphere/Liberty application server), and **ADM** (the admin/build container). Optional add-on roles are **MOCK** and **SMTP**. |
| **Environment template** (a.k.a. *image config*) | A reusable recipe — which DB/APP/ADM images to use, which database vendor, ports, volumes, and which build pipeline — that new environments are stamped out from. In the UI these live under **Environments**. |
| **Action** | A single command run against a container role — e.g. "Build EAR" on ADM, "Restart WebSphere" on APP. Actions are either **built-in** (shipped with Monohull) or **custom** (yours). |
| **Pipeline** | An ordered sequence of actions. The build pipeline is what turns a set of empty containers into a working Maximo. |
| **Connected repository** | A git repo Monohull watches so it can build every pull request — as a build check, or as a full throwaway environment. |

### The life of an environment

```
 create ──▶ CREATING ──▶ BUILDING ──▶ CONFIGURING ──▶ RUNNING
                                                          │
                                          stop ◀──────────┤──────────▶ remove
                                           │              │
                                        STOPPED ──▶ start ┘
```

When you create an environment, Monohull pulls the configured images, creates a
dedicated Docker network, launches the DB/APP/ADM containers, and then runs the
**build pipeline** (build the EAR, run UpdateDB, apply fixes, start the app
server, reset credentials). The whole thing streams to the browser live. When it
reaches `RUNNING`, the environment is ready to log into.

You can **stop** an environment (containers stopped but preserved), **start** it
again, or **remove** it (containers stopped and deleted). Its build history and
config stay in Monohull's database until you remove it.

---

## Signing in & getting around

Monohull requires a login. On first boot an admin account is seeded from the
`MONOHULL_ADMIN_USERNAME` / `MONOHULL_ADMIN_PASSWORD` the installer set. Enter those on
the **Sign in** screen.

The left **sidebar** is the primary navigation:

| Item | What it's for |
|---|---|
| **Dashboard** | Your running/stopped environments. The home base. |
| **Environments** | Environment **templates** (image configs) — the recipes new environments are built from. |
| **Actions** | Built-in and custom actions (commands you can run on containers). |
| **Pipelines** | Ordered sequences of actions. |
| **Repositories** | Connect git repos for per-PR builds. |
| **Registry** | Credentials for pulling images from a private Docker registry. |

The user panel at the bottom of the sidebar shows who you're signed in as and a
**sign-out** button. On a phone the sidebar collapses behind the menu icon in the
top bar.
