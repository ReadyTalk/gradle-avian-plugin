package com.ecovate.gradle.plugins.avian

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


abstract class AvianTask extends DefaultTask {

	public static final String JAVA_HOME = System.getProperty('java.home')
	
	
	protected String mainClass
	protected boolean library
	
	protected String buildPlatform
	protected String buildArch
	protected String platform
	protected String arch
	protected String version
	protected String avianVersion
	protected String avian
	protected File stage1
	protected File stage2
	protected File output
	
	protected boolean proguard
	
	
	protected abstract void addDependencies()
	
	protected void init() {
		mainClass = project.avian.mainClass
		library = project.avian.library
		
		buildPlatform = project.avian.buildPlatform
		buildArch = project.avian.buildArch
		
		platform = project.avian.platform
		arch = project.avian.arch
		
		version = project.avian.version
		avianVersion = project.avian.avianVersion
		
		proguard = project.avian.proguard
		
		avian = project.avian.avian
		stage1 = project.avian.stage1
		stage2 = project.avian.stage2
		output = project.avian.output
	}

	def runCommand(command) {
		return project.avian.runCommand(command)
	}

	def runCommand(command, dir) {
		return project.avian.runCommand(command, dir)
	}
	
	def tree(name) {
		return project.avian.tree(project.file(name))
	}
		
}
