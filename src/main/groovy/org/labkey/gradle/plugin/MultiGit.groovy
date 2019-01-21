package org.labkey.gradle.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.operation.BranchListOp
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicResponseHandler
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.json.JSONObject
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.util.PropertiesUtils

import java.util.stream.Collectors

/**
 * This is an incubating feature set.  Interfaces and functionality are likely to change, perhaps drastically,
 * before it is released.
 *
 * This plugin can be used to get data about a gradle project that is comprised of multiple git repositories.
 * It uses the GitHub GraphQL API (https://developer.github.com/v4/) to query for a set of repositories. Using
 * the properties gitTopcis, requireAllTopics, and includeArchived, a user is able to filter to a certain set
 * of repositories.  See the task descriptions for more info.
 *
 * In order to use this plugin, you need to create an access token for GitHub
 * (https://developer.github.com/v4/guides/forming-calls/#authenticating-with-graphql)
 * Once generated, set the GIT_ACCESS_TOKEN environment variable to this value.
 *
 * Apply this plugin at the root of the enlistment.
 *
 */
class MultiGit implements Plugin<Project>
{

    class Repository
    {
        enum Type
        {
            clientLibrary,
            gradlePlugin,
            serverModule,
            other,
            svnModule, // This should be removed eventually
            svnCustomModule, // This should be removed eventually
            svnExternalModule // this should be removed eventually
        }

        private String name
        private String description
        private String licenseName
        private String licenseURL
        private String url
        private Boolean isPrivate = false
        private Boolean isExternal = false
        private Boolean isArchived = false
        private Boolean isSvn = false
        private Type type = Type.other
        private Project project
        private File enlistmentDir
        private Project rootProject
        private Properties moduleProperties
        private String dependencies
        private String supportedDatabases = "mssql,pgsql"

        Repository(Project rootProject, String name, Boolean isSvn)
        {
            this.name = name
            this.isSvn = isSvn
            if (isSvn)
            {
                if (rootProject.file("server/modules/${name}").exists())
                    this.setType(Type.svnModule)
                else if (rootProject.file("server/customModules/${name}").exists())
                    this.setType(Type.svnCustomModule)
                else if (rootProject.file("externalModules/${name}").exists())
                    this.setType(Type.svnExternalModule)
            }
            setProject(rootProject)
        }

        Repository(Project rootProject, String name, String url, Boolean isPrivate, Boolean isArchived, List<String> topics)
        {
            this.rootProject = rootProject
            this.name = name
            this.url = url
            this.isArchived = isArchived
            this.isPrivate = isPrivate
            if (topics.contains("labkey-external"))
            {
                this.setIsExternal(true)
            }
            if (topics.contains("labkey-module"))
            {
                this.setType(Type.serverModule)
            }
            else if (topics.contains("labkey-client-api"))
            {
                this.setType(Type.clientLibrary)
            }
            else if (topics.contains("gradle-plugin"))
            {
                this.setType(Type.gradlePlugin)
            }
            else
            {
                this.setType(Type.other)
            }
            setProject(rootProject)
        }


        String getName()
        {
            return name
        }

        void setName(String name)
        {
            this.name = name
        }

        String getDescription()
        {
            return description
        }

        void setDescription(String description)
        {
            this.description = description
        }

        String getUrl()
        {
            return url
        }

        void setUrl(String url)
        {
            this.url = url
        }

        String getLicenseName()
        {
            return licenseName
        }

        void setLicenseName(String licenseName)
        {
            this.licenseName = licenseName
        }

        String getLicenseURL()
        {
            return licenseURL
        }

        void setLicenseURL(String licenseURL)
        {
            this.licenseURL = licenseURL
        }

        Boolean getIsPrivate()
        {
            return isPrivate
        }

        void setIsPrivate(Boolean isPrivate)
        {
            this.isPrivate = isPrivate
        }

        Boolean getIsExternal()
        {
            return isExternal
        }

        void setIsExternal(Boolean isExternal)
        {
            this.isExternal = isExternal
        }

        Boolean getIsArchived()
        {
            return isArchived
        }

        void setIsArchived(Boolean isArchived)
        {
            this.isArchived = isArchived
        }

        Boolean getIsSvn()
        {
            return isSvn
        }

        void setIsSvn(Boolean isSvn)
        {
            this.isSvn = isSvn
        }

        String getDependencies()
        {
            return dependencies
        }

        void setDependencies(String dependencies)
        {
            this.dependencies = dependencies
        }

        String getSupportedDatabases()
        {
            return supportedDatabases
        }

        void setSupportedDatabases(String supportedDatabases)
        {
            this.supportedDatabases = supportedDatabases
        }

        File getEnlistmentDir()
        {
            return enlistmentDir
        }

        void setEnlistmentDir(File enlistmentDir)
        {
            this.enlistmentDir = enlistmentDir
        }

        boolean haveEnlistment()
        {
            return getEnlistmentDir().exists()
        }

        void enlist()
        {
            enlist(null)
        }

        private Grgit getGit()
        {
            File enlistmentDir = getEnlistmentDir();

            if (enlistmentDir.exists())
            {
                rootProject.logger.info("${this.getName()}: enlistment already exists in expected location")
                return Grgit.open {
                    dir = enlistmentDir
                }
            }
            return null;
        }


        void enlist(String branchName)
        {
            File directory = getEnlistmentDir()

            Grgit git = getGit()

            if (git == null)
            {
                String url = this.getUrl()
                rootProject.logger.quiet("${this.getName()}: Cloning repository into ${directory}")
                git = Grgit.clone({
                    dir = directory
                    uri = url
                })
            }

            if (!StringUtils.isEmpty(branchName))
            {
                rootProject.logger.info("${this.getName()}: Checking out branch '${branchName}'")
                if (git.branch.list().find( { it.name == branchName }))
                    git.checkout (branch: branchName)
                else
                    git.checkout(branch: branchName, startPoint: "origin/${branchName}", createBranch: true)
            }
        }

        private String getCheckoutProject()
        {
            switch (getType())
            {
                case Type.clientLibrary:
                    return ":remoteapi"
                case Type.gradlePlugin:
                    return ":buildSrc"
                case Type.serverModule:
                    return isExternal ? ":externalModules" : ":server:optionalModules"
                case Type.svnModule:
                    return ":server:modules"
                case Type.svnCustomModule:
                    return ":server:customModules"
                case Type.svnExternalModule:
                    return ":externalModules"
            }
            return "";
        }

        void setProject(Project rootProject)
        {
            String path = getProjectPath()
            if (rootProject.findProject(path) != null)
            {
                project = rootProject.project(path)

                enlistmentDir = project.projectDir
            }
            else
            {
                enlistmentDir = rootProject.file(path.substring(1).replaceAll(":", File.separator))
            }
            if (enlistmentDir.exists())
            {
                this.moduleProperties = new Properties()

                File propertiesFile = new File(enlistmentDir, ModuleExtension.MODULE_PROPERTIES_FILE)
                if (propertiesFile.exists())
                {
                    PropertiesUtils.readProperties(propertiesFile, this.moduleProperties)

                    if (this.moduleProperties.containsKey("License"))
                        this.setLicenseName((String) this.moduleProperties.get("License"))
                    if (this.moduleProperties.containsKey("LicenseURL"))
                        this.setLicenseURL((String) this.moduleProperties.get("LicenseURL"))
                    if (this.moduleProperties.containsKey("Description") && !StringUtils.isEmpty(((String) this.moduleProperties.get("Description")).trim()))
                        this.setDescription((String) this.moduleProperties.get("Description"))
                    if (this.moduleProperties.containsKey("ModuleDependencies"))
                        this.setDependencies((String) this.moduleProperties.get("ModuleDependencies"))
                    if (this.moduleProperties.containsKey("SupportedDatabases"))
                        this.setSupportedDatabases((String) this.moduleProperties.get("SupportedDatabases"))
                }
            }
        }

        // Hmmm. This doesn't really work as intended for a few reasons:
        //  - if the project is not in the settings file, it will be null
        //  - the ModuleDependencies property in the module.properties file list modules, which does not include the path required to get to that module (e.g., modules/base/some_module)
        List<String> getModuleDependencies()
        {
            if (project == null)
                return []

            List<String> moduleNames = []
            if (project.configurations.findByName('modules') != null)
            {
                project.configurations.modules.dependencies.forEach({
                    Dependency dep ->
                        moduleNames.add(dep.getName())
                })
            }
            else if (project.file(ModuleExtension.MODULE_PROPERTIES_FILE).exists())
            {
                Properties props = new Properties()
                props.load(new FileInputStream(project.file(ModuleExtension.MODULE_PROPERTIES_FILE)))
                if (props.hasProperty(ModuleExtension.MODULE_DEPENDENCIES_PROPERTY))
                {
                    for (String name : ((String) props.get(ModuleExtension.MODULE_DEPENDENCIES_PROPERTY)).split(","))
                        moduleNames.add(name.strip())
                }
            }

            return moduleNames;
        }

        Project getProject()
        {
            return project
        }

        String getProjectPath()
        {
            return getCheckoutProject() + ":" + getName()
        }

        Type getType()
        {
            return type
        }

        void setType(Type type)
        {
            this.type = type
        }

        Branch getCurrentBranch()
        {
            Grgit git = getGit();
            return git != null ?  git.branch.current : null
        }
        String toString()
        {
            return toString(false)
        }

        /**
         * :server:optionalModules:biologics
         *         Type: private active serverModule
         *         License: LabKey Software License (https://www.labkey.com/license)
         *         Repo: https://github.com/LabKey/biologics
         * :server:optionalModules:tnprc_ehr
         *         Type: private active serverModule
         *         Description: LabKey Server module for Tulane National Primate Research Center's EHR implementation
         *         License: Apache License 2.0
         *         Repo: https://github.com/LabKey/tnprc_ehr
         * :server:optionalModules:workflow
         *         Description: Workflow processing engine and services
         *         License: Apache License 2.0
         *         Repo: https://github.com/LabKey/workflow
         *         Type: public active serverModule
         *
         * :server:optionalModules:biologics (https://github.com/LabKey/biologics) - [serverModule] Description here
         * :server:optionalModules:tnprc_ehr (https://github.com/LabKey/tnprc_ehr) - [serverModule] LabKey Server module for Tulane National Primate Research Center's EHR implementation
         * :server:optionalModules:workflow (https://github.com/LabKey/workflow) - [serverModule] Workflow processing engine and services
         * @param verbose
         * @return
         */
        String toString(Boolean verbose)
        {
            StringBuilder builder =  new StringBuilder()
            if (verbose)
            {
                builder.append("${this.getProjectPath()}\n")
                builder.append("\tType: ${this.isPrivate ? 'private' : 'public'} ${this.isArchived ? 'archived' : 'active'} ${this.getType()}\n")
                builder.append("\tDescription: ${this.getDescription() == null ? '' : this.getDescription()}\n")
                builder.append("\tLicense: ${this.getLicenseName() == null ? "unknown" : this.getLicenseName()}")
                if (this.getLicenseURL() != null)
                    builder.append(" (${this.getLicenseURL()})")
                builder.append("\n")
                if (this.dependencies != null)
                    builder.append("\tModule Dependencies: ${this.getDependencies()}\n")
                builder.append("\tRepoURL: ${this.getUrl()}\n")
                builder.append("\tSupported Databases: ${this.supportedDatabases}")
            }
            else
            {
                builder.append("${this.getProjectPath()} (${this.url}) - [${this.type}] ")
            }

            return builder.toString()
        }
    }

    private Project project;
    private static final String GITHUB_GRAPHQL_ENDPOINT = "https://api.github.com/graphql"
    private static final String TOPICS_PROPERTY = "gitTopics"
    private static final String ALL_TOPICS_PROPERTY = "requireAllTopics"
    private static final String INCLUDE_ARCHIVED_PROPERTY = "includeArchived"

    @Override
    void apply(Project project)
    {
        this.project = project
        addTasks(project);
    }

    private static Map<String, Object> getMap(Map<String, Object> data, List<String> path)
    {
        Map<String, Object> current = data

        for (String step: path)
        {
            current = (Map<String, Object>) current.get(step);
        }
        return current;
    }

    private static List<Map<String, Object>> getMapList(Map<String, Object> response, List<String> path, String finalKey)
    {
        Map<String, Object> current = getMap(response, path)
        return (List<Map<String, Object>>) current.get(finalKey)
    }

    // enlist in all of the repositories that make up LabKey server (moduleSet=all or no moduleSet provided)
    // enlist in all of the projects currently included (can point to a settings file that lists project individually and get an enlistment that way)
    // enlist in a set of modules filtered by gitHub topics.
    Collection<Repository> getEnlistmentBaseSet(Map<String, Repository> repositories)
    {
        if (!project.hasProperty('moduleSet') || project.property('moduleSet') == 'all')
            return repositories.values()

        Set<Repository> baseRepos = new HashSet<Repository>()
        project.subprojects({
            Project sub ->
                String[] pathParts = sub.path.split(":")

                boolean found = false
                // We walk backwards because the project names must be unique, but intermediate directories (e.g., optionalModules) may have
                // separate git repos.
                for (int i = pathParts.size()-1; i >= 0 && !found; i--)
                {
                    if (repositories.containsKey(pathParts[i]))
                    {
                        baseRepos.add(repositories.get(pathParts[i]))
                        found = true
                    }
                }
        })

        return baseRepos;
    }

    void addTasks(Project project)
    {
        project.tasks.register("gitRepoList") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) List all Git repositories. Use -Pverbose to show more details. Use -P${TOPICS_PROPERTY} to filter to modules with certain topics.  " +
                        "This can be a comma-separated list of topics (e.g., labkey-module,labkey-client-api).  By default, all repositories with any of these topics will be listed.  " +
                        "Use -P${ALL_TOPICS_PROPERTY} to specify that all topics must be present.  " +
                        "By default, only repositories that have not been archived are listed.  Use -P${INCLUDE_ARCHIVED_PROPERTY} to also include archived repositories."
                task.doLast({
                    Map<String, Repository> repos = this.getRepositoriesViaSearch(project)
                    StringBuilder builder = new StringBuilder()
                    builder.append(getEchoHeader(repos, project))
                    builder.append("\n")
                    for (Repository repo : repos.values().sort({Repository rep -> rep.getProjectPath()}))
                    {
                        builder.append("${repo.toString(project.hasProperty('verbose'))}\n")
                    }
                    println(builder.toString())
                })
        }

        project.tasks.register("gitBranches")  {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Show the current branches for each of the repos in which there is an enlistment. " +
                        "Use the properties ${TOPICS_PROPERTY}, ${ALL_TOPICS_PROPERTY}, and ${INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for filtering. " +
                        "N.B. This task relies on accurate tagging of the Git repositories so it can determine the expected enlistment directory."
                task.doLast({
                    Map<String, Repository> repos = this.getRepositoriesViaSearch(project)

                    StringBuilder builder = new StringBuilder(getEchoHeader(repos, project))
                        .append("\n\n")
                        .append("The current branches for each of the git repositories:\n")
                    Map<String, Set<String>> branches = new HashMap<>()
                    for (Repository repo : repos.values().sort({Repository rep -> rep.getProjectPath()}))
                    {
                        if (repo.haveEnlistment())
                        {
                            String branchName = repo.getCurrentBranch().name
                            if (!branches.containsKey(branchName))
                                branches.put(branchName, new TreeSet<>())
                            branches.get(branchName).add(repo.getProjectPath())
                            builder.append("${repo.projectPath} - ${branchName}\n")
                        }
                    }
                    builder.append("\n")
                    builder.append("The projects enlisted in each branch:\n")
                    branches.keySet().forEach({
                        String key ->
                            builder.append("${key} - ${branches.get(key)}\n")
                    })

                    println(builder.toString())
                })
        }

        project.tasks.register("gitCheckout") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) For all repositories with a current enlistment, perform a git checkout for the branch provided by the 'branch' property (e.g., -Pbranch=release18.3).  " +
                        "If no such branch exists for a repository, leaves the enlistment as is.  " +
                        "Use the properties ${TOPICS_PROPERTY}, ${ALL_TOPICS_PROPERTY}, and ${INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for filtering."
                task.doLast({
                    if (!project.hasProperty('branch'))
                        throw new GradleException("Property 'branch' must be defined.")
                    String branchName = (String) project.property('branch')
                    String remoteBranch = "origin/${branchName}"
                    List<Repository> toUpdate = new ArrayList<>()
                    Map<String, Repository> repositories = getRepositoriesViaSearch(project)
                    project.logger.quiet(getEchoHeader(repositories, project))
                    repositories.values().forEach({
                        Repository repository ->
                            if (repository.enlistmentDir.exists())
                            {
                                Grgit grgit = Grgit.open {
                                    dir = repository.enlistmentDir
                                }
                                List<Branch> branches = grgit.branch.list(mode: BranchListOp.Mode.REMOTE)

                                boolean hasBranch = branches.stream().anyMatch({
                                    Branch branch ->
                                        branch.name == remoteBranch
                                })
                                if (hasBranch)
                                    if (grgit.branch.current().name != branchName)
                                        toUpdate.push(repository)
                                    else
                                        project.logger.quiet("${repository.projectPath}: already on branch '" + branchName + "'. No checkout required.")
                                else
                                    project.logger.quiet("${repository.projectPath}: no branch '" + branchName + "'. No checkout attempted.")
                            }
                    })
                    toUpdate.forEach({
                        Repository repository ->
                            repository.enlist(branchName)
                    })
                })
        }

        // TODO support the -Pbranch property.  Almost there, but for the case that the branch does not exist.
        project.tasks.register("gitEnlist") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Enlist in all of the git modules used for a running LabKey server.  " +
                        "Use the properties ${TOPICS_PROPERTY}, ${ALL_TOPICS_PROPERTY}, and ${INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for filtering the repository set. " +
                        "If a moduleSet property is specified, enlist in only the modules included by that module set. Using -PmoduleSet=all is the same as providing no module set property."
                task.doLast({
                    // get all the repositories
                    Map<String, Repository> repositories = getRepositoriesViaSearch(project)
                    // get the starting point for enlistment
                    Collection<Repository> baseSet = getEnlistmentBaseSet(repositories)
                    project.logger.quiet(getEchoHeader(repositories, project))
                    project.logger.quiet("Finding enlistments starting with: " +
                            "${baseSet.stream().map( {repository -> repository.getProjectPath()}).collect(Collectors.joining(", "))}" +
                            "\n")
                    // initialize the container for the repos we've already visited
                    Set<String> enlisted = new HashSet<>()
                    enlisted.add("optionalModules") // add this separately because you don't actually want this repository in a normal enlistment
                    // do recursive enlistment for all the base repositories
                    baseSet.forEach({
                        Repository repository ->
                            enlist(repositories, repository, enlisted)
                    })
                })
        }

        //
        // TODO Add tasks for releasing
        // - branch
        // - release
    }

    private String getEchoHeader(Map<String, Repository> repositories, Project project)
    {
        StringBuilder builder = new StringBuilder("Considering ${repositories.size()} git repositories")
        if (project.hasProperty(TOPICS_PROPERTY))
        {
            builder.append(project.hasProperty(ALL_TOPICS_PROPERTY) ? " with all of the topics: " : " with any of the topics: ")
            builder.append(project.property(TOPICS_PROPERTY))
        }
        return builder.toString()
    }

    private void enlist(Map<String, Repository> repositories, Repository repository, Set<String> enlisted, String branch = null)
    {
        if (!enlisted.contains(repository.getName()))
        {
            enlisted.add(repository.getName())
            if (!repository.haveEnlistment())
            {
                project.logger.quiet("Enlisting in ${repository.getName()} in directory ${repository.getEnlistmentDir()}")
                repository.enlist(branch)
                if (repository.getProject() == null)
                    repository.setProject(project)
            }
            else
            {
                project.logger.quiet("Already have enlistment for ${repository.getName()} in directory ${repository.getEnlistmentDir()}")
            }
            // Hmmm.  Can't get module dependencies when the project is not included in settings.gradle,
            // So how do you bootstrap?  I want to start with a minimal enlistment and end up with an
            // enlistment that includes all the repos I need for a given distribution
            repository.getModuleDependencies().forEach({
                String name ->
                    if (!enlisted.contains(name))
                    {
                        project.logger.quiet("Adding dependency ${name} to possible enlistments")
                        // N.B. This relies on the name of the repository being the same as the name of the Gradle project
                        if (repositories.containsKey(name))
                        {
                            enlist(repositories, (Repository) repositories.get(name), enlisted, branch)
                        }
                        else
                        {
                            Repository svnRepo = new Repository(project, name, true)
                            if (svnRepo.getEnlistmentDir().exists())
                                project.logger.quiet("Already have svn enlistment for ${svnRepo.getName()} in ${svnRepo.getEnlistmentDir()}")
                            else
                                project.logger.warn("WARNING: No repository found for dependency '${svnRepo.getProjectPath()}'.")
                        }
                    }
            })
        }
    }

    private static String getAuthorizationToken()
    {
        return System.getenv('GIT_ACCESS_TOKEN');
    }

    private static String getQuerySearchString(boolean includeArchived, String repoTopics = "", String prevEndCursor = null)
    {
        String queryString = "org:LabKey "
        if (!includeArchived)
            queryString += " archived:false "
        queryString += repoTopics
        String cursorString = prevEndCursor == null ? "after:null" : "after:\"${prevEndCursor}\""
        return "query {search(query: \"${queryString}\", type:REPOSITORY, first:100, ${cursorString} ) {  " +
                " repositoryCount " +
                "    pageInfo {" +
                "      endCursor" +
                "      hasNextPage" +
                "      startCursor" +
                "    }" +
                " edges { " +
                "   node { " +
                "       ... on Repository { " +
                "           name " +
                "           description " +
                "           isArchived " +
                "           isPrivate " +
                "           licenseInfo {" +
                "               name" +
                "           }" +
                "           url " +
                "           repositoryTopics(first: 10) {" +
                "               edges {" +
                "                   node {" +
                "                       topic {" +
                "                           name" +
                "                       }" +
                "                   }" +
                "               }" +
                "           }" +
                "       } " +
                "   }  " +
                "} " +
            "}     " +
        "}"
    }

    private static List<String> getRepositoryTopics(Map<String, Object> node)
    {
        List<String> names = new ArrayList<>()
        List<Map<String, Object>> topicEdges = getMapList(node, ['repositoryTopics'], 'edges')
        for (Map<String, Object> topicEdge : topicEdges)
        {
            Map<String, Object> topicMap = getMap(topicEdge, ['node', 'topic'])
            names.push(((String) topicMap.get('name')).toLowerCase())
        }
        return names;
    }

    private void setLicenseInfo(Repository repository, Map<String, Object> node)
    {
        if (repository.getLicenseName() == null)
        {
            Map<String, Object> licenseInfo = (Map<String, Object>) node.get('licenseInfo')
            if (licenseInfo != null)
            {
                repository.setLicenseName((String) licenseInfo.get('name'))
            }
        }
    }

    private Map<String, Repository> getAllRepositories(Project project, boolean includeArchived, String filterString)
    {

        boolean hasNextPage = true
        String endCursor = null
        Map<String, Repository> repositories = new TreeMap<>()

        while (hasNextPage)
        {
            project.logger.info("getAllRepositories - includeArchived: ${includeArchived}, filterString: '${filterString}'")
            Map<String, Object> rawData = makeRequest(project, getQuerySearchString(includeArchived, filterString, endCursor))
            Map<String, Object> pageInfo = getMap(rawData, ['data', 'search', 'pageInfo']);
            hasNextPage = (Boolean) pageInfo.get('hasNextPage')
            endCursor = (String) pageInfo.get('endCursor')
            project.logger.info("hasNextPage ${hasNextPage} endCursor ${endCursor}")

            List<Map<String, Object>> searchResults = getMapList(rawData, ['data', 'search'], "edges");
            for (Map<String, Object> nodeMap: searchResults)
            {
                Map<String, Object> node = (Map<String, Object>) nodeMap.get('node')
                String name = node.get('name')
                String description = (node.get('description') != null) ? node.get('description') : ''
                List<String> topics = getRepositoryTopics(node)

                Repository repository = new Repository(project, name, (String) node.get('url'), (Boolean) node.get('isPrivate'), (Boolean) node.get('isArchived'), topics)

                if (!StringUtils.isEmpty(description) && StringUtils.isEmpty(repository.getDescription()))
                    repository.setDescription(description)
                setLicenseInfo(repository, node)

                repositories.put(repository.getName(), repository)
            }
        }
        return repositories;
    }

    private Map<String, Repository> getRepositoriesViaSearch(Project project) throws IOException
    {
        boolean includeArchived = !project.hasProperty(INCLUDE_ARCHIVED_PROPERTY)
        boolean requireAllTopics = project.hasProperty(ALL_TOPICS_PROPERTY)

        Map<String, Repository> repositories = new TreeMap<>()

        List<String> topicFilters = project.hasProperty(TOPICS_PROPERTY) ?
                ((String) project.property(TOPICS_PROPERTY)).split(",")  : []
        if (requireAllTopics || topicFilters.isEmpty())
        {
            String filterString = topicFilters.stream().map({topic -> topic.trim()}).collect(Collectors.joining(" topic:"))
            repositories = getAllRepositories(project, includeArchived, filterString)
        }
        else
        {
            topicFilters.forEach({
                topic ->
                    repositories.putAll(getAllRepositories(project, includeArchived, "topic:${topic.trim()}"))
            })
        }

        return repositories
    }

    private static Map<String, Object> makeRequest(Project project, String queryString) throws IOException
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        Map<String, Object> rawData
        project.logger.info("Making request with queryString '${queryString}'")
        try
        {
            HttpPost httpPost = new HttpPost(GITHUB_GRAPHQL_ENDPOINT);
            httpPost.setHeader("Authorization", "Bearer " + getAuthorizationToken())

            Map<String, String> requestObject = new HashMap<>()
            requestObject.put("query", queryString)
            httpPost.setEntity(new StringEntity(JSONObject.valueToString(requestObject), ContentType.APPLICATION_JSON))

            CloseableHttpResponse response = httpClient.execute(httpPost)
            try
            {
                ResponseHandler<String> handler = new BasicResponseHandler()
                String contents = handler.handleResponse(response)
                project.logger.info("Response contents ${contents}")
                ObjectMapper mapper = new ObjectMapper()
                rawData = mapper.readValue(contents, Map.class)
            }
            catch (Exception re)
            {
                throw new GradleException("Problem retrieving response from query '${queryString}'", re);
            }
            finally
            {
                response.close();
            }
        }
        catch (Exception e)
        {
            throw new GradleException("Problem executing request for repository data with query '${queryString}'", e)
        }
        finally
        {
            httpClient.close()
        }
        if (rawData == null)
            throw new GradleException("No data retrieved with query '${queryString}'")
        if (rawData != null && rawData.containsKey("errors"))
            throw new GradleException("Problem retrieving repository with query, '${queryString}': ${rawData.get('errors')}")

        return rawData
    }
}
