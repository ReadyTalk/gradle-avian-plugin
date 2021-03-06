package com.ecovate.gradle.plugins.avian

import java.text.SimpleDateFormat;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPlugin


class AvianPlugin implements Plugin<Project> {

	public static final String AVIAN_GROUP = "Avian"
	public static final String defaultAvianVersion = '0.5.51-SNAPSHOT'

	private static String adjustArch(arch) {
		switch(arch) {
		case ~/.*64.*/: return 'x86_64'
		default:        return 'i386'
		}
	}
	
	private static String adjustPlatform(pltfrm) {
		switch(pltfrm.toLowerCase()) {
		case ~/.*linux.*/:  return 'linux'
		case ~/.*darwin.*/: return 'darwin'
		case ~/.*mac.*/:    return 'darwin'
		case ~/.*win.*/:    return 'windows'
		default:            return pltfrm
		}
	}


	private Project project
	private Logger logger

	private Boolean library
	private Boolean proguard
	private Boolean buildexe

	private String buildPlatform
	private String buildArch

	private String platform
	private String arch
	
	private String name
	private String version
	private String fullVersion
	
	private String mainClass
	private String avianVersion

	private File avian
	private File stage1
	private File stage2
	private File output
	
	private AvianTask initAvianTask, initVersionTask, buildExeTask

	@Override
	public void apply(Project project) {
		this.project = project
		this.logger = project.logger
		
		setBuildProps()
		
		project.plugins.apply(JavaPlugin.class)

		project.metaClass.tree << { obj -> return this.tree(project.file(obj)) }
		
		project.task('setup') << { setup() }
		
		initAvianTask = project.task('initAvian',
			group: AVIAN_GROUP,
			description: "Initializes the Avian classpath",
			type: InitAvianTask
		)
		
		initVersionTask = project.task('initVersion',
			group: AVIAN_GROUP,
			description: "Initializes the Application version",
			type: InitVersionTask
		)
		
		buildExeTask = project.task('buildExe',
			group: AVIAN_GROUP,
			description: "Build the executable",
			type: BuildExeTask
		)
		
		project.tasks.initAvian.dependsOn   project.tasks.setup
		project.tasks.compileJava.dependsOn project.tasks.initAvian
		project.tasks.compileJava.dependsOn project.tasks.initVersion
		project.tasks.assemble.dependsOn project.tasks.buildExe
		project.tasks.buildExe.dependsOn project.tasks.initAvian
		project.tasks.buildExe.dependsOn project.tasks.jar
		
		project.ext.setProperty('avian', this)
	}

	public void setAvianVersion(String version) {
		this.avianVersion = version
	}

	public void setLibrary(boolean library) {
		this.library = library
	}
	
	public void setMainClass(String mainClass) {
		this.mainClass = mainClass
	}

	private void setup() {
		platform = project.platform
		arch = project.arch
		
		name = project.hasProperty('executable.name') ? project.property('executable.name') : project.name
		version = project.hasProperty('executable.version') ? project.property('executable.version') : project.version
		if(version.endsWith('.qualifier')) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss")
			version = version.substring(0, version.length()-10)
			fullVersion = version + '.v' + sdf.format(new Date())
		} else {
			fullVersion = version
		}

		if(library == null) {
			library = (project.hasProperty('library') && project.library) ? true : false;
		}
		
		if(!library) {
			proguard = (project.hasProperty('proguard') && !project.proguard) ? false : true;
			buildexe = (project.hasProperty('buildexe') && !project.buildexe) ? false : true;
		}

		if(mainClass == null && (proguard || buildexe)) {
			if(project.hasProperty('mainClass')) {
				mainClass = String.valueOf(project.mainClass)
			} else {
				throw new IllegalArgumentException('mainClass must be set in order to proguard or build the executable')
			}
		}
		
		if(avianVersion == null) {
			avianVersion = project.hasProperty('avianVersion') ? project.avianVersion : defaultAvianVersion
		}

		if(project.hasProperty('avian-location')) {
			avian = new File(project.getProperty('avian-location'))
		} else {
			avian = project.file("build/avian/$platform-$arch")
		}
		stage1 = project.file('build/stage1')

		if(!library) {
			stage2 = project.file('build/stage2')
			output = project.file('build/output')
		}
		
		initAvianTask.init()
		initVersionTask.init()
		buildExeTask.init()

		initAvianTask.addDependencies()
		buildExeTask.addDependencies()
	}

	private void setBuildProps() {
		buildArch = adjustArch(System.properties['os.arch'])
		buildPlatform = adjustPlatform(System.properties['os.name'])
	}
	
	def runCommand(command) {
		runCommand(command, null)
	}

	def runCommand(command, dir) {
		println command.join(' ')
		Process result = (dir == null) ? command.execute() : command.execute(null, project.file(dir))
		def output = new StringBuffer()
		result.consumeProcessOutput(output, output)
		result.waitFor()
		if (result.exitValue() == 0) {
			logger.log(LogLevel.INFO, output.toString())
		} else {
			throw new RuntimeException(output.toString())
		}
	}

	def tree(File file, boolean explodeJar = true) {
		if(file.isDirectory()) return project.fileTree(file, {})
		if(file.name.endsWith('.zip')) return project.zipTree(file)
		if(file.name.endsWith('.tar.gz')) return project.tarTree(file)
		if(explodeJar && file.name.endsWith('.jar')) return project.zipTree(file)
		return file
	}

}
