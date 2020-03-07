# `@atomist/update-maven-dependencies`

<!---atomist-skill-readme:start--->

Update Maven dependencies tracks all of the dependencies found in project's pom.xml files.  The skill can be configured
to automatically create Pull Requests when a library version is discovered to be out of sync with a target policy.

## Configuration

### Name

Give your configuration of this skill a distinctive name so that you you will recognize it when you have more than one enabled. 
Something helpful like "latest releases from NpmJs.org" would be appropriate for a configuration that helps 
track bug fixes in any open source libraries used by your projects.

### policy

Each configuration of this skill allows you to choose how you track target versions of libraries.

* `manual` - this policy is appropriate when you'll manually choose a target version to be used by your projects.  

As part of configuring the manual policy, a set of `coordinates` must be entered.  These refer to the target pom.xml dependencies that your 
project should use.  They must be in the form `groupId:artifactId:version`
           
* `latest semver used` - this policy will detect the latest version of a library used in your Repositories.  
                         Any Repo using older versions will receive Pull Requests to update them to the latest in use.

As part of configuring the `latest semver used` policy, a set of `artifacts` must be entered.  This set of artifacts 
should be in the form `groupId:artifactId`.  The policy will be applied to only this set of artifacts.  
                         
### Which repositories

By default, this skill will be enabled for all repositories in all organizations you have connected.
To restrict the organizations or specific repositories on which the skill will run, you can explicitly
choose organization(s) and repositories.

## Integrations

**GitHub**

The Atomist GitHub integration must be configured to used this skill. At least one repository must be selected.

**Slack**

If the Atomist Slack integration is configured, this skill will send a notification message to the configured Slack channel when a pull request is created. 

<!---atomist-skill-readme:end--->

---

Created by [Atomist][atomist].
Need Help?  [Join our Slack workspace][slack].

[atomist]: https://atomist.com/ (Atomist - How Teams Deliver Software)
[slack]: https://join.atomist.com/ (Atomist Community Slack) 
