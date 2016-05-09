//
// TODO: Get all of this information from the Update Center via a REST API.
//

//
// A Categorized list of the plugins offered for install in the wizard.
// This is a community curated list.
// Remember, the user ultimately has full control as they can easily customize
// away from these.
//
exports.availablePlugins = [
    {
        "category":"Organization and Administration",
        "plugins": [
            { "name": "dashboard-view" },
            { "name": "cloudbees-folder", "suggested": true },
            { "name": "antisamy-markup-formatter", "suggested": true }
        ]
    },
    {
        "category":"Build Features",
        "description":"Add general purpose features to your jobs",
        "plugins": [
            { "name": "build-name-setter" },
            { "name": "build-timeout", "suggested": true },
            { "name": "config-file-provider" },
            { "name": "credentials-binding", "suggested": true },
            { "name": "embeddable-build-status" },
            { "name": "rebuild" },
            { "name": "ssh-agent" },
            { "name": "throttle-concurrents" },
            { "name": "timestamper", "suggested": true },
            { "name": "ws-cleanup", "suggested": true }
        ]
    },
    {
        "category":"Build Tools",
        "plugins": [
            { "name": "ant", "suggested": true },
            { "name": "gradle", "suggested": true },
            { "name": "msbuild" },
            { "name": "nodejs" }
        ]
    },
    {
        "category":"Build Analysis and Reporting",
        "plugins": [
            { "name": "checkstyle" },
            { "name": "cobertura" },
            { "name": "htmlpublisher" },
            { "name": "junit" },
            { "name": "warnings" },
            { "name": "xunit" }
        ]
    },
    {
        "category":"Pipelines and Continuous Delivery",
        "plugins": [
            { "name": "workflow-aggregator", "suggested": true, "added": "2.0" },
            { "name": "github-organization-folder", "suggested": true, "added": "2.0" },
            { "name": "pipeline-stage-view", "suggested": true, "added": "2.0" },
            { "name": "build-pipeline-plugin" },
            { "name": "conditional-buildstep" },
            { "name": "jenkins-multijob-plugin" },
            { "name": "parameterized-trigger" },
            { "name": "copyartifact" }
        ]
    },
    {
        "category":"Source Code Management",
        "plugins": [
            { "name": "bitbucket" },
            { "name": "clearcase" },
            { "name": "cvs" },
            { "name": "git", "suggested": true },
            { "name": "git-parameter" },
            { "name": "github" },
            { "name": "gitlab-plugin" },
            { "name": "p4" },
            { "name": "repo" },
            { "name": "subversion", "suggested": true },
            { "name": "teamconcert" },
            { "name": "tfs" }
        ]
    },
    {
        "category":"Distributed Builds",
        "plugins": [
            { "name": "matrix-project" },
            { "name": "ssh-slaves", "suggested": true },
            { "name": "windows-slaves" }
        ]
    },
    {
        "category":"User Management and Security",
        "plugins": [            
            { "name": "matrix-auth", "suggested": true },
            { "name": "pam-auth", "suggested": true },
            { "name": "ldap", "suggested": true },
            { "name": "role-strategy" },
            { "name": "active-directory" }
        ]
    },
    {
        "category":"Notifications and Publishing",
        "plugins": [
            { "name": "email-ext", "suggested": true },
            { "name": "emailext-template" },
            { "name": "mailer", "suggested": true },
            { "name": "publish-over-ssh" },
            // { "name": "slack" }, // JENKINS-33571, https://github.com/jenkinsci/slack-plugin/issues/191
            { "name": "ssh" }
        ]
    }
];
