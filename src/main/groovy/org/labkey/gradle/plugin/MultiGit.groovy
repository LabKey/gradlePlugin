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
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ProjectDependency
import org.json.JSONObject
import org.labkey.gradle.plugin.extension.ModuleExtension
import org.labkey.gradle.util.PropertiesUtils

class MultiGit implements Plugin<Project>
{

    class Repository
    {
        enum Type
        {
            clientLibrary,
            gradlePlugin,
            serverModule,
            serverModuleContainer,
            other
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
        private Type type = Type.clientLibrary
        private Project project
        private File enlistmentDir
        private Project rootProject
        private Properties moduleProperties
        private String dependencies
        private String supportedDatabases = "mssql,pgsql"

        Repository(String name)
        {
            this.name = name;
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
            if (topics.contains("labkey-module-container"))
            {
                this.setType(Type.serverModuleContainer)
            }
            else if (topics.contains("labkey-module"))
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
                rootProject.logger.info("${this.getName()}: Cloning repository into ${directory}")
//                    git = Grgit.clone({
//                        dir = directory
//                        uri = this.getUrl()
//                    })
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
                    return isExternal ? ":externalModules" : isSvn ? ":server:modules" : ":server:optionalModules"
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
                enlistmentDir = rootProject.file(path.replaceAll(":", File.separator).substring(1))
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

        DependencySet getModuleDependencies()
        {
            return project != null ? project.configurations.modules.dependencies : null
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

    @Override
    void apply(Project project)
    {
        this.project = project
        addTasks(project);
    }

    private static Map<String, Object> getMap(Map<String, Object> data, List<String> path)
    {
        Map<String, Object> current = data;

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

    Collection<Repository> getEnlistmentBaseSet(Map<String, Repository> repositories)
    {
        Set<Repository> baseRepos = new HashSet<Repository>()

        Project distProject = project.project(":distributions:bio")
        distProject.configurations.distribution.dependencies.forEach({
            Dependency dep ->
                println("${dep.group} ${dep.name} ${dep.version}")
                if (dep instanceof ProjectDependency)
                    println(((ProjectDependency) dep).dependencyProject.path)
        })

        String[] enlistmentSet = project.hasProperty("enlistmentSet") ? ((String) project.property("enlistmentSet")).split(",")  : null
        if (enlistmentSet == null || enlistmentSet.contains("all"))
            return repositories.values()



        // TODO If given a distribution project as the enlistmentSet, find the project's distribution dependencies (recursively)
        // Will need to detect the :distributions prefix and get an enlistment in that repo to start if not available
        //
        // if given a regular project as the module set, find the modules dependencies (recursively)
        for (String name : enlistmentSet)
        {
            if (!repositories.containsKey(name))
                project.logger.error("No repository named '${name}'")
            else
                baseRepos.add(repositories.get(name))
        }
        return baseRepos;
    }

    void addTasks(Project project)
    {
        project.tasks.register("gitRepoList") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) List all Git repositories. Use -Pverbose to show more details."
                task.doLast({
//                    Map<String, Repository> repos = this.getRepositories()
                    Map<String, Repository> repos = this.getRepositoriesViaSearch(project)
                    StringBuilder builder = new StringBuilder();
                    for (Repository repo : repos.values().sort({Repository rep -> rep.getProjectPath()}))
                    {
                        builder.append("${repo.toString(project.hasProperty('verbose'))}\n")
                    }
                    println(builder.toString())
                })
        }

        // gitRepoList - list the available repositories
        // gitEnlistAll - finds all the available repositories and enlists in all of them.
        //              If given a distribution path, will enlist in the modules within the distribution (that have SNAPSHOT versions?)
        //              If given a module and the transitive flag, will enlist in all the repositories for modules the given module depends on
        // gitCheckoutAll - Checks out a given branch for all repositories that have that branch.
        // showBranches - list all the current branches for human scrutiny

        project.tasks.register("gitBranches")  {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Show the current branches for each of the repos in which there is an enlistment"
                task.doLast({
                    Map<String, Repository> repos = this.getRepositoriesViaSearch(project)
                    StringBuilder builder = new StringBuilder("The current branches for each of the git repositories:\n")
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
                    println("The projects enlisted in each branch:")
                    branches.keySet().forEach({
                        String key ->
                            println("${key} - ${branches.get(key)}")
                    })

                    println()
                    println(builder.toString())
                })
        }

        project.tasks.register("gitCheckout")  {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) For all repositories with a current enlistment, check out the branch provided by the 'branch' property (e.g., -Pbranch=release18.3).  If no such branch exists for a repository, leaves the enlistment as is."
                task.doLast({
                    if (!project.hasProperty('branch'))
                        throw new GradleException("Property 'branch' must be defined.")
                    String branchName = (String) project.property('branch')
                    String remoteBranch = "origin/${branchName}"
                    List<Repository> toUpdate = new ArrayList<>()
                    Map<String, Repository> repositories = getRepositoriesViaSearch(project)
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
                                } )
                                if (hasBranch)
                                    if (grgit.branch.current.name != branchName)
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



        project.tasks.register("enlist") {
            Task task ->
                task.group = "VCS"
//                task.description = "(incubating) Enlist in a all of the git modules specified by the 'enlistmentSet' property.  The value of enlistmentSet may be a distribution, a module, or 'all' (default) "
                task.description = "(incubating) Enlist in a all of the git modules used for a running LabKey server."
                task.doLast({
                    // get all the repositories
                    Map<String, Repository> repositories = getRepositoriesViaSearch(project)
                    // get the starting point for enlistment
                    Collection<Repository> baseSet = getEnlistmentBaseSet(repositories)
                    // initialize the container for the repos we've already visited
                    Set<String> enlisted = new HashSet<>()
                    // do recursive enlistment for all the base repositories
                    baseSet.forEach({
                        Repository repository ->
                            enlist(repositories, repository, enlisted)
                    })
                })
        }

        //
        // TODO Releasing
        // - branch
        // - release
    }

    private void enlist(Map<String, Repository> repositories, Repository repository, Set<String> enlisted)
    {
        project.logger.quiet("Enlisting in ${repository.getName()}")
        if (!enlisted.contains(repository.getName()))
        {
            enlisted.add(repository.getName())
            if (!repository.haveEnlistment())
            {
                repository.enlist()
                if (repository.getProject() == null)
                    repository.setProject(project)
            }
            // Hmmm.  Can't get module dependencies when the project is not included in settings.gradle,
            // So how do you bootstrap?  I want to start with a minimal enlistment and end up with an
            // enlistment that includes all the repos I need for a given distribution
            repository.getModuleDependencies().forEach({
                Dependency dep ->
                    project.logger.quiet("Adding ${dep.getName()} to enlistments")
                    // N.B. This relies on the name of the repository being the same as the name of the Gradle project
                    if (repositories.containsKey(dep.getName()))
                        enlist(repositories, repositories.get(dep.getName()), enlisted)
                    else
                    {
                        // TODO refine this
                        Repository svnRepo = new Repository(dep.getName())
                        svnRepo.setIsSvn(true)
                        if (!project.findProject(svnRepo.getProjectPath()))
                            project.logger.quiet("No repository found for dependency '${dep.getName()}'.  I hope that's in svn.")
                    }
            })
            // TODO what about bootstrap and labkey-client-api?
            // TODO capture somewhere the projects that need to be added to settings.gradle
        }
    }

    private static String getAuthorizationToken()
    {
        return System.getenv('GIT_ACCESS_TOKEN');
    }

    private static String getQueryString()
    {
        return "query { organization(login:\"LabKey\") { repositories(first:100, orderBy: {direction: ASC, field: NAME}) { nodes { description name url isArchived isPrivate repositoryTopics(first: 10) { edges { node { topic { name } } } } } } } }"
    }

    private static String getQuerySearchString(boolean includeClientApi, boolean includeModules, boolean onlyActive)
    {
        // TODO paging
        String queryString = "org:LabKey "
        if (onlyActive)
            queryString += " archived:false "
        if (includeModules)
            queryString += " topic:labkey-module"
        if (includeClientApi)
            queryString += " topic:labkey-client-api"
        return "query {search(query: \"${queryString}\", type:REPOSITORY, first:100) {  " +
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

    private Map<String, Repository> getRepositoriesViaSearch(Project project, String[] searchTopics) throws IOException
    {
        boolean onlyActive = !project.hasProperty('includeArchived') || !(Boolean) project.property('includeArchived')
        // TODO this invites more generality; not using this for now since we don't have tags on most repositories
//        boolean includeModules = !project.hasProperty('repoTags') || ((String) project.property('repoTags')).contains("labkey-module")
        Map<String, Object> rawData = makeRequest(getQuerySearchString(false, true, onlyActive))
        // TODO collect for individual tags separately
        // TODO loop through pages

        Map<String, Repository> repositories = new TreeMap<>()

        List<Map<String, Object>> searchResults = getMapList(rawData, ['data', 'search'], "edges");
        for (Map<String, Object> nodeMap: searchResults)
        {
            Map<String, Object> node = (Map<String, Object>) nodeMap.get('node')
            String name = node.get('name')
            String description = (node.get('description') != null) ? node.get('description') : ''
            List<String> topics = getRepositoryTopics(node);

            Repository repository = new Repository(project, name, (String) node.get('url'), (Boolean) node.get('isPrivate'), (Boolean) node.get('isArchived'), topics)

            if (!StringUtils.isEmpty(description) && StringUtils.isEmpty(repository.getDescription()))
                repository.setDescription(description)
            setLicenseInfo(repository, node)

            repositories.put(repository.getName(), repository)
        }

        return repositories;
    }


    private Map<String, Repository> getRepositories() throws IOException
    {
        Map<String, Object> rawData = makeRequest(getQueryString())

        Map<String, Repository> repositories = new TreeMap<>()

        List<Map<String, Object>> nodes = getMapList(rawData, ['data', 'organization', 'repositories'], "nodes");
        for (Map<String, Object> node: nodes)
        {
            String name = node.get('name')
            String description = (node.get('description') != null) ? node.get('description') : ''
            repositories.put(name.toLowerCase(), new Repository(name, description, (String) node.get('url'), (Boolean) node.get('isPrivate')))
        }
        return repositories
    }

    private static Map<String, Object> makeRequest(String queryString) throws IOException
    {
        CloseableHttpClient httpClient = HttpClients.createDefault();

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
                ObjectMapper mapper = new ObjectMapper()
                return mapper.readValue(contents, Map.class)
            }
            catch (Exception re)
            {
                throw new GradleException("Problem retrieving response from query", re);
            }
            finally
            {
                response.close();
            }
        }
        catch (Exception e)
        {
            throw new GradleException("Problem executing request for repository data", e)
        }
        finally
        {
            httpClient.close()
        }
    }
}
