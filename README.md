# `@atomist/update-maven-dependencies`

<!---atomist-skill-readme:start--->

# What it's useful for

Track all the different versions of mvn artifacts in `pom.xml` files across your
repositories.  
Automatically raise pull requests when a version does not conform to a selected
target version. Choose from three different targets:

-   **Latest Used** - select the latest version found in one of your
    repositories
-   **Manual** - manually select a target version in a skill configuration

# Before you get started

Connect and configure these integrations:

1. **GitHub**
2. **Slack** (optional)

The **GitHub** integration must be configured in order to use this skill. At
least one repository must be selected. We recommend connecting the **Slack**
integration.

When the optional Slack integration is enabled, users can interact with this
skill directly from Slack.

# How to configure

You can enable this skill without configuring any target versions. In this mode,
the skill will collect data about your artifact versions, but will take no
action. Simply select the set of repositories that should be scanned.

1. **Select dependency target policy, optional policy configuration**

    A `Manual` policy requires that you specify both the library and the
    version.

    ![screenshot1](docs/images/screenshot1.png)

    The other two policies require only the names of the libraries that should
    be kept up to date.

    ![screenshot2](docs/images/screenshot2.png)

2. **Determine repository scope**

    ![Repository filter](docs/images/repo-filter.png)

    By default, this skill will be enabled for all repositories in all
    organizations you have connected.

    To restrict the organizations or specific repositories on which the skill
    will run, you can explicitly choose organization(s) and repositories.

## How to use Update Maven Dependencies

1.  **Configure the skill, add a target policy and select repositories to scan
    for `pom.xml` files**

    The skill will run on any new pushes to selected repositories. and will
    raise pull requests for artifacts that are not on target.

2.  **Run a version sync from Slack**

    Interactively check that a repository is in sync with current policies.

    ```
    @atomist mvn sync
    @atomist mvn sync --slug=org/repo
    ```

    (you do not need to specify a `--slug` parameter if your Slack channel is
    linked to a repository)

    ![screenshot4](docs/images/screenshot4.png)

    This is useful when you want to raise a pull request without having to wait
    for a push to occur.

3.  ** Enjoy an easier way to keep your dependencies as current as you want them
    to be**

To create feature requests or bug reports, create an
[issue in the repository for this skill](https://github.com/atomist-skills/update-maven-dependencies-skill/issues).
See the
[code](https://github.com/atomist-skills/update-maven-dependencies-skill) for
the skill.

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist]. Need Help? [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ "Atomist - How Teams Deliver Software"
[slack]: https://join.atomist.com/ "Atomist Community Slack"
