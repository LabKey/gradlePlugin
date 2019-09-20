package org.labkey.gradle.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import org.ajoberstar.grgit.Branch
import org.ajoberstar.grgit.Credentials
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Status
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

import java.text.SimpleDateFormat
import java.util.stream.Collectors

import static org.labkey.gradle.plugin.MultiGit.RepositoryQuery.getAuthorizationToken

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
    class PullRequest
    {
        private String author
        private String title
        private String updatedAt
        private String state
        private String url
        private String headRefName // name of the branch
        private String baseRefName //where it was merged to

        String getAuthor()
        {
            return author
        }

        void setAuthor(String author)
        {
            this.author = author
        }

        String getTitle()
        {
            return title
        }

        void setTitle(String title)
        {
            this.title = title
        }

        String getUpdatedAt()
        {
            return updatedAt
        }

        void setUpdatedAt(String updatedAt)
        {
            this.updatedAt = updatedAt
        }

        String getState()
        {
            return state
        }

        void setState(String state)
        {
            this.state = state
        }

        String getUrl()
        {
            return url
        }

        void setUrl(String url)
        {
            this.url = url
        }

        String getHeadRefName()
        {
            return headRefName
        }

        void setHeadRefName(String headRefName)
        {
            this.headRefName = headRefName
        }

        String getBaseRefName()
        {
            return baseRefName
        }

        void setBaseRefName(String baseRefName)
        {
            this.baseRefName = baseRefName
        }

        String toString()
        {
            return toString("listing")
        }

        String toString(String format)
        {
            StringBuilder builder = new StringBuilder()
            if (format == null || format.equals("listing"))
            {
                builder.append("Author: ${this.author}; ")
                builder.append("HeadRef: ${this.headRefName}; ")
                builder.append("BaseRef: ${this.baseRefName}; ")
                builder.append("State: ${this.state}; ")
                builder.append("Url: ${this.url}; ")
                builder.append("UpdatedAt: ${this.updatedAt}; ")
                builder.append("Title: ${this.title}")
            }
            else if (format.equals("tsv"))
                builder.append("${this.author}\t${this.getHeadRefName()}\t${this.getBaseRefName()}\t${this.getState()}\t${this.getUrl()}\t${this.getUpdatedAt()}\t${this.getTitle()}")
            return builder.toString()
        }

        static String getTsvHeader()
        {
            return "Author\tHeadRef\tBaseRef\tState\tUrl\tUpdatedAt\tTitle"
        }
    }

    class Repository
    {
        enum Type
        {
            clientLibrary('labkey-client-api', ":remoteapi"),
            gradlePlugin('gradle-plugin', ":buildSrc"),
            serverModule("labkey-module", ":server:modules"),
            serverExternalModule("labkey-external", ":externalModules"),
            serverModuleContainer("labkey-module-container", ":server:modules"),
            serverOptionalModule("labkey-optional-module", ":server:optionalModules"),
            testContainer("labkey-test-container", ":server"),
            other(null, ""),
            svnExternalModule(null, ":externalModules") // this should be removed eventually

            private String topic
            private String enlistmentProject

            private Type(topic, enlistmentProject)
            {
                this.topic = topic;
                this.enlistmentProject = enlistmentProject
            }
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
        private List<PullRequest> pullRequests

        Repository(Project rootProject, String name, Boolean isSvn)
        {
            this.name = name
            this.isSvn = isSvn
            if (isSvn)
            {
                if (rootProject.file("externalModules/${name}").exists())
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
            if (topics.contains('labkey-optional-module'))
            {
                this.setType(Type.serverOptionalModule)
            }
            else if (topics.contains('labkey-module-container'))
            {
                this.setType(Type.serverModuleContainer)
            }
            else if (topics.contains("labkey-module"))
            {
                this.setType(Type.serverModule)
            }
            else if (topics.contains('labkey-test-container'))
            {
                this.setType(Type.testContainer)
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
                    currentDir = enlistmentDir
                    credentials = new Credentials(getAuthorizationToken())
                }
            }
            return null
        }

        boolean hasRemoteBranch(String remoteBranch)
        {
            Grgit grgit = getGit()
            grgit.fetch(prune: true)
            List<Branch> branches = grgit.branch.list(mode: BranchListOp.Mode.REMOTE)

            return branches.stream().anyMatch({
                Branch branch ->
                   return branch.name == remoteBranch
            })
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
                if (hasRemoteBranch("origin/${branchName}"))
                {
                    if (git.branch.list().find( { it.name == branchName }))
                    {
                        rootProject.logger.quiet("${this.getName()}: Checking out branch '${branchName}'")
                        git.checkout(branch: branchName)
                    }
                    else
                    {
                        rootProject.logger.quiet("${this.getName()}: Checking out branch '${branchName}' (origin/${branchName})")
                        git.checkout(branch: branchName, startPoint: "origin/${branchName}", createBranch: true)
                    }
                }
                else
                {
                    rootProject.logger.quiet("${this.getName()}: No branch '${branchName} found.  Leaving on default branch.")
                }
            }
        }

        private String getCheckoutProject()
        {
            return isExternal ? ":externalModules" : getType().enlistmentProject;
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
                rootProject.logger.info("getting enlistment directory from path '${path}'")
                enlistmentDir = rootProject.file(path.substring(1).replace(":", File.separator))
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

        List<PullRequest> getPullRequests()
        {
            return pullRequests
        }

        void setPullRequests(List<PullRequest> pullRequests)
        {
            this.pullRequests = pullRequests
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
                builder.append("\tSupported Databases: ${this.supportedDatabases}\n")
                List<PullRequest> prs = this.getPullRequests()
                if (prs != null && !prs.isEmpty())
                {
                    builder.append("\tPull Requests:\n")
                    for (PullRequest pr : prs)
                    {
                        builder.append("\t\t${pr.toString()}\n")
                    }
                }
            }
            else
            {
                builder.append("${this.getProjectPath()} (${this.url}) - [${this.type}] ")
            }

            return builder.toString()
        }
    }

    class RepositoryQuery
    {
        private static final String GITHUB_GRAPHQL_ENDPOINT = "https://api.github.com/graphql"
        public static final String TOPICS_PROPERTY = "gitTopics"
        public static final String ALL_TOPICS_PROPERTY = "requireAllTopics"
        public static final String INCLUDE_PRS_PROPERTY = "includePullRequests"
        public static final String INCLUDE_ARCHIVED_PROPERTY = "includeArchived"
        public static final String PR_STATES_PROPERTY = "prStates"
        public static final String BASE_BRANCH_PROPERTY = "baseBranch"
        public static final String START_DATE_PROPERTY = "prStartDate"
        public static final String END_DATE_PROPERTY = "prEndDate"
        public static final int REPO_PAGE_SIZE = 100
        public static final int PR_PAGE_SIZE = 20
        private static final SimpleDateFormat updatedAtFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        private static final SimpleDateFormat dateRangeFormat = new SimpleDateFormat("yyyy-MM-dd")


        private boolean includeLicenseInfo = false
        private boolean includeTopics = false
        private boolean includePullRequests = false
        private boolean includeArchived = false
        private boolean requireAllTopics = false
        private Date startDate
        private Date endDate
        private String baseRef
        private String prStates
        private List<String> topicFilters

        RepositoryQuery(Project project)
        {
            includeArchived = project.hasProperty(INCLUDE_ARCHIVED_PROPERTY)
            includePullRequests = project.hasProperty(INCLUDE_PRS_PROPERTY)
            requireAllTopics = project.hasProperty(ALL_TOPICS_PROPERTY)
            prStates = project.hasProperty(PR_STATES_PROPERTY) ? project.property(PR_STATES_PROPERTY) : null
            startDate = project.hasProperty(START_DATE_PROPERTY) ? dateRangeFormat.parse((String) project.property(START_DATE_PROPERTY)) : null
            endDate = project.hasProperty(END_DATE_PROPERTY) ? dateRangeFormat.parse((String) project.property(END_DATE_PROPERTY)) : null
            baseRef = project.hasProperty(BASE_BRANCH_PROPERTY) ? project.property(BASE_BRANCH_PROPERTY) : null
            topicFilters = project.hasProperty(TOPICS_PROPERTY) ?
                    ((String) project.property(TOPICS_PROPERTY)).split(",")  : []
        }

        boolean getIncludeLicenseInfo()
        {
            return includeLicenseInfo
        }

        void setIncludeLicenseInfo(boolean includeLicenseInfo)
        {
            this.includeLicenseInfo = includeLicenseInfo
        }

        boolean getIncludeTopics()
        {
            return includeTopics
        }

        void setIncludeTopics(boolean includeTopics)
        {
            this.includeTopics = includeTopics
        }

        boolean getIncludePullRequests()
        {
            return includePullRequests
        }

        void setIncludePullRequests(boolean includePullRequests)
        {
            this.includePullRequests = includePullRequests
        }

        private static String getAuthorizationToken()
        {
            return System.getenv('GIT_ACCESS_TOKEN');
        }

        private String getQueryString(String filterString = "")
        {
            String queryString = "org:LabKey ${filterString} "
            if (!includeArchived)
                queryString += " archived:false "
            return "\"${queryString}\", type:REPOSITORY, first:${REPO_PAGE_SIZE} "
        }

        private getRepositorySelectors()
        {
            String selectors =
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
                            "           url "
            if (includeLicenseInfo)
                selectors +=
                        "           licenseInfo {" +
                                "               name" +
                                "           }"
            if (includeTopics)
                selectors +=
                        "           repositoryTopics(first: 10) {" +
                                "               edges {" +
                                "                   node {" +
                                "                       topic {" +
                                "                           name" +
                                "                       }" +
                                "                   }" +
                                "               }" +
                                "           }"
            if (includePullRequests)
            {
                String baseRefFilter  = baseRef == null ? "" : ", baseRefName: \"${baseRef}\""
                String statesFilter = prStates == null ? "" : ", states: [${prStates.toUpperCase()}]"

                String pullRequestFilter = "first:${PR_PAGE_SIZE} ${statesFilter} ${baseRefFilter}, orderBy: {field: UPDATED_AT, direction: DESC} "
                selectors +=
                        "          pullRequests(${pullRequestFilter}) {" +
                                "           totalCount" +
                                "           pageInfo {" +
                                "              hasPreviousPage" +
                                "           } " +
                                "           nodes { " +
                                "               author {" +
                                "                   login " +
                                "               }" +
                                "               updatedAt" +
                                "               state" +
                                "               title" +
                                "               url" +
                                "               headRefName" +
                                "               baseRefName" +
                                "           } " +
                                "  } "
            }
            selectors +=
                    "       } " +
                    "   }  " +
                    "} "
            return selectors
        }


        private String getSearchString(String filterString, String prevEndCursor = null)
        {
            String cursorString = prevEndCursor == null ? "after:null" : "after:\"${prevEndCursor}\""
            return "query {\n" +
                    "  search(query: ${getQueryString(filterString)} ${cursorString}){" +
                    "    ${getRepositorySelectors()}" +
                    "  } " +
                    "}"

        }

        private static List<String> getRepositoryTopics(Map<String, Object> node)
        {
            List<String> names = new ArrayList<>()
            if (node.containsKey("repositoryTopics"))
            {
                List<Map<String, Object>> topicEdges = getMapList(node, ['repositoryTopics'], 'edges')
                for (Map<String, Object> topicEdge : topicEdges)
                {
                    Map<String, Object> topicMap = getMap(topicEdge, ['node', 'topic'])
                    names.push(((String) topicMap.get('name')).toLowerCase())
                }
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

        private boolean isInDateRange(Date updatedDate)
        {
            if (startDate == null && endDate == null)
                return true
            if (startDate != null && updatedDate.before(startDate))
                return false
            if (endDate != null && updatedDate.after(endDate))
                return false

            return true
        }

        private void setPullRequestInfo(Repository repository, Map<String, Object> node)
        {
            List<PullRequest> pullRequests = new ArrayList<>()
            List<Map<String, Object>> prNodes = getMapList(node, ['pullRequests'], 'nodes')
            for (Map<String, Object> prNode : prNodes)
            {
                Date updatedDate = updatedAtFormat.parse((String) prNode.get("updatedAt"))
                if (isInDateRange(updatedDate))
                {
                    PullRequest pr = new PullRequest()
                    pr.setTitle((String) prNode.get("title"))
                    pr.setState((String) prNode.get("state"))
                    pr.setUpdatedAt((String) prNode.get('updatedAt'))
                    pr.setUrl((String) prNode.get("url"))
                    pr.setBaseRefName((String) prNode.get('baseRefName'))
                    pr.setHeadRefName((String) prNode.get('headRefName'))
                    Map<String, Object> author = (Map<String, Object>) prNode.get("author")
                    pr.setAuthor((String) author.get('login'))
                    pullRequests.push(pr)
                }
            }
            repository.setPullRequests(pullRequests)
        }

        private Map<String, Repository> getAllRepositories(Project project, String filterString)
        {

            boolean hasNextPage = true
            String endCursor = null
            Map<String, Repository> repositories = new TreeMap<>()

            while (hasNextPage)
            {
                project.logger.info("getAllRepositories - includeArchived: ${includeArchived}, filterString: '${filterString}'")
                Map<String, Object> rawData = makeRequest(project, getSearchString(filterString, endCursor))
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
                    // TODO pageInfo for pullRequests
                    if (includePullRequests)
                        setPullRequestInfo(repository, node)

                    repositories.put(repository.getName(), repository)
                }
            }
            return repositories;
        }

        Map<String, Repository> execute() throws IOException
        {
            Map<String, Repository> repositories = new TreeMap<>()

            if (requireAllTopics || topicFilters.isEmpty())
            {
                String filterString = topicFilters.stream().map({topic -> topic.trim()}).collect(Collectors.joining(" topic:"))
                repositories = getAllRepositories(project, filterString)
            }
            else
            {
                topicFilters.forEach({
                    topic ->
                        repositories.putAll(getAllRepositories(project, "topic:${topic.trim()}"))
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

    private Project project;

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
                task.description = "(incubating) List all Git repositories. Use -Pverbose to show more details. Use -P${RepositoryQuery.TOPICS_PROPERTY} to filter to modules with certain topics.  " +
                        "This can be a comma-separated list of topics (e.g., labkey-module,labkey-client-api).  By default, all repositories with any of these topics will be listed.  " +
                        "Use -P${RepositoryQuery.ALL_TOPICS_PROPERTY} to specify that all topics must be present.  " +
                        "By default, only repositories that have not been archived are listed.  Use -P${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} to also include archived repositories." +
                        "Use -P${RepositoryQuery.INCLUDE_PRS_PROPERTY} in conjunction with -Pverbose to include info on pull requests.  Use the properties as in the 'listPullRequests' tasks to filter the pull requests."
                task.doLast({
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeLicenseInfo(true)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repos = query.execute()
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
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for filtering. " +
                        "N.B. This task relies on accurate tagging of the Git repositories so it can determine the expected enlistment directory."
                task.doLast({
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repos = query.execute()

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
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for filtering."
                task.doLast({
                    if (!project.hasProperty('branch'))
                        throw new GradleException("Property 'branch' must be defined.")
                    String branchName = (String) project.property('branch')
                    String remoteBranch = "origin/${branchName}"
                    List<Repository> toUpdate = new ArrayList<>()
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repositories = query.execute()
                    project.logger.quiet(getEchoHeader(repositories, project))

                    repositories.values().each({
                        Repository repository ->
                            if (repository.enlistmentDir.exists())
                            {
                                if (repository.hasRemoteBranch(remoteBranch))
                                    if (repository.git.branch.current().name != branchName)
                                        toUpdate.push(repository)
                                    else
                                        project.logger.info("${repository.projectPath}: already on branch '" + branchName + "'. No checkout required.")
                                else
                                    project.logger.info("${repository.projectPath}: no branch '" + branchName + "'. No checkout attempted.")
                            }
                    })
                    toUpdate.each({
                        Repository repository ->
                            try
                            {
                                repository.enlist(branchName)
                            }
                            catch (Exception e)
                            {
                                project.logger.error("${repository.projectPath}: problem enlisting in branch '" + branchName + "'. ${e.message}")
                            }
                    })
                })
        }

        project.tasks.register("gitStatus") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Perform a 'git status' for a collection of repositories. " +
                        "By default, uses the projects specified in the settings.gradle file for which there is a current enlistment. " +
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for " +
                        "choosing a set of repositories other than those given in the settings.gradle file."
                task.doLast({
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repositories = query.execute()
                    project.logger.quiet(getEchoHeader(repositories, project))
                    repositories.values().forEach({
                        Repository repository ->
                            if (repository.enlistmentDir.exists() && (project.hasProperty(RepositoryQuery.TOPICS_PROPERTY) || repository.project != null))
                            {
                                Grgit grgit = Grgit.open {
                                    currentDir = repository.enlistmentDir
                                }
                                Status status = grgit.status()
                                if (status.isClean())
                                {
                                    project.logger.quiet("${repository.enlistmentDir}: up to date on branch ${grgit.branch.current().name}")
                                }
                                else
                                {
                                    List<String> messages = new ArrayList<String>()
                                    String msg = "Staged changes: "
                                    if (status.staged.allChanges.size() > 0)
                                    {
                                        msg += " ${status.staged.added.size()} additions"
                                        msg += " ${status.staged.modified.size()} modifications"
                                        msg += " ${status.staged.removed.size()} removals"

                                    }
                                    else
                                    {
                                        msg += "None"
                                    }
                                    messages.add(msg)

                                    msg = "Unstaged changes: "
                                    if (status.unstaged.allChanges.size() > 0)
                                    {
                                        msg += " ${status.unstaged.added.size()} additions"
                                        msg += " ${status.unstaged.modified.size()} modifications"
                                        msg += " ${status.unstaged.removed.size()} removals"
                                    }
                                    else
                                    {
                                        msg += "None"
                                    }
                                    messages.add(msg)
                                    if (status.conflicts.size() > 0)
                                    {
                                        messages.add("${status.conflicts.size()} unresolved conflicts")
                                    }
                                    project.logger.quiet("${repository.enlistmentDir}:")
                                    project.logger.quiet("\t${messages.join("\n\t")}")
                                }
                            }
                    })
                })
        }

        project.tasks.register("gitPull") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Perform a 'git pull' for a collection of repositories. " +
                        "Use -PgitRebase to rebase the branches while pulling." +
                        "By default, uses the projects specified in the settings.gradle file for which there is a current enlistment. " +
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for " +
                        "choosing a set of repositories other than those given in the settings.gradle file."
                task.doLast({
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repositories = query.execute()
                    project.logger.quiet(getEchoHeader(repositories, project))
                    repositories.values().forEach({
                        Repository repository ->
                            if (repository.enlistmentDir.exists() && (project.hasProperty(RepositoryQuery.TOPICS_PROPERTY) || repository.project != null))
                            {
                                project.logger.quiet("Pulling for ${repository.enlistmentDir} ")
                                Grgit grgit = Grgit.open {
                                    currentDir = repository.enlistmentDir
                                }
                                grgit.pull(rebase: project.hasProperty('gitRebase'))
                            }
                    })
                })
        }

        project.tasks.register("gitFetch") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Perform a 'git fetch' for a collection of repositories.  " +
                        "Use -PgitPrune to remove any remote-tracking references that no longer exist on the remote." +
                        "By default, uses the projects specified in the settings.gradle file for which there is a current enlistment. " +
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for " +
                        "choosing a set of repositories other than those given in the settings.gradle file."
                task.doLast({
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repositories = query.execute()
                    project.logger.quiet(getEchoHeader(repositories, project))
                    repositories.values().forEach({
                        Repository repository ->
                            if (repository.enlistmentDir.exists() && (project.hasProperty(RepositoryQuery.TOPICS_PROPERTY) || repository.project != null))
                            {
                                project.logger.quiet("Fetching for ${repository.enlistmentDir}")
                                Grgit grgit = Grgit.open {
                                    currentDir = repository.enlistmentDir
                                }
                                grgit.fetch(prune: project.hasProperty('gitPrune'))
                            }
                    })
                })
        }


        project.tasks.register("gitPush") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Perform a `git push` for a collection of repositories. " +
                        "Use -PgitDryRun to do everything except actually send the updates." +
                        "By default, uses the projects specified in the settings.gradle file for which there is a current enlistment. " +
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for " +
                        "choosing a set of repositories other than those given in the settings.gradle file."
                task.doLast({
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repositories = query.execute()
                    project.logger.quiet(getEchoHeader(repositories, project))
                    repositories.values().forEach({
                        Repository repository ->
                            if (repository.enlistmentDir.exists() && (project.hasProperty(RepositoryQuery.TOPICS_PROPERTY) || repository.project != null))
                            {
                                project.logger.quiet("Pushing for ${repository.enlistmentDir}")
                                Grgit grgit = Grgit.open {
                                    currentDir = repository.enlistmentDir
                                }
                                grgit.push(dryRun: project.hasProperty('gitDryRun'))
                            }
                    })
                })
        }

        project.tasks.register("gitEnlist") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Enlist in all of the git modules used for a running LabKey server.  " +
                        "Use -Pbranch=<bname> to enlist in a particular branch (shortcut for using gitEnlist then gitCheckout -Pbranch=<name>)."
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for filtering the repository set. " +
                        "If a moduleSet property is specified, enlist in only the modules included by that module set. Using -PmoduleSet=all is the same as providing no module set property."
                task.doLast({
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludeTopics(true)
                    Map<String, Repository> repositories = query.execute()
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
                            enlist(repositories, repository, enlisted,  (String) project.property('branch'))
                    })
                })
        }

        project.tasks.register("listPullRequests") {
            Task task ->
                task.group = "VCS"
                task.description = "(incubating) Lists the pull requests for a set of modules... TODO" +
                        "Use the properties ${RepositoryQuery.TOPICS_PROPERTY}, ${RepositoryQuery.ALL_TOPICS_PROPERTY}, and ${RepositoryQuery.INCLUDE_ARCHIVED_PROPERTY} as for the 'gitRepoList' task for filtering the repository set. " +
                        "Use ${RepositoryQuery.BASE_BRANCH_PROPERTY} to sepcify the base for the pull requests (default: develop)." +
                        "Use -P${RepositoryQuery.PR_STATES_PROPERTY}=[MERGED|CLOSED|OPEN] to specify the state(s) of the pull requests to show (default: all). " +
                        "Use -P${RepositoryQuery.START_DATE_PROPERTY}=YYYY-MM-DD and -P${RepositoryQuery.END_DATE_PROPERTY}=YYYY-MM-DD to filter by pull dates"

                        task.doLast( {
                    // get all the repositories
                    RepositoryQuery query = new RepositoryQuery(project)
                    query.setIncludePullRequests(true)
                    Map<String, Repository> repositories = query.execute()
                    project.logger.quiet(getEchoHeader(repositories, project))
                    project.logger.quiet("Repository\t" + PullRequest.getTsvHeader())
                    for (Repository repo : repositories.values())
                    {
                        for (PullRequest pr : repo.getPullRequests())
                        {
                            project.logger.quiet("${repo.getName()}\t${pr.toString('tsv')}")
                        }
                    }
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
        if (project.hasProperty(RepositoryQuery.TOPICS_PROPERTY))
        {
            builder.append(project.hasProperty(RepositoryQuery.ALL_TOPICS_PROPERTY) ? " with all of the topics: " : " with any of the topics: ")
            builder.append(project.property(RepositoryQuery.TOPICS_PROPERTY))
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


}
