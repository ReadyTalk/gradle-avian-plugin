package com.ecovate.gradle.plugins.avian

import java.io.File
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


class BuildExeTask extends AvianTask {

	File platformFiles
	File platformResources
	
	@Override
	protected void addDependencies() {
		project.dependencies {
			if(platform.equals('darwin')) {
				compile "com.ecovate.avian-darwin-x86_64:avian-$platform-$arch:$avianVersion@tar.gz"
			} else {
				compile "com.ecovate.avian-linux-x86_64:avian-$platform-$arch:$avianVersion@tar.gz"
			}
			if(proguard) {
				compile "proguard:proguard:4.6"
			}
			if(platform.equals('windows') && buildPlatform.equals('linux')) {
				if(arch.equals('i386')) {
					compile "com.ecovate.compilers:win32:1.0.0"
				} else {
					compile "com.ecovate.compilers:win64:1.0.0"
				}
			}
		}
	}
	
	private void compileMain(dir) {
		logger.lifecycle "building $dir/main.o"

		project.file("$dir/main.cpp").write(
			project.file('main.cpp').text.replace('${MAIN_CLASS}', mainClass.replace('.','/'))
		)

		if(platform.equals('windows')) {
			if(buildPlatform.equals('linux')) {
				def cmd = arch.equals('i386') ? ['x86_64-w64-mingw32-g++', '-m32', '-march=i586'] : ['x86_64-w64-mingw32-g++']
				runCommand(cmd + [
					'-fno-exceptions', '-fno-rtti',
					"-I$platformFiles/include", '-D_JNI_IMPLEMENTATION_',
					'-c', "$dir/main.cpp",
					'-o', "$dir/main.o"
				])
			}
			else {
				throw new RuntimeException("TODO: build $platform from $buildPlatform")
			}
			return
		}
		
		if(platform.equals('linux')) {
			if(buildPlatform.equals('linux')) {
				runCommand([
		            'g++', "-I$JAVA_HOME/../include", "-I$JAVA_HOME/../include/linux", '-D_JNI_IMPLEMENTATION_',
		            '-c', "$dir/main.cpp",
		            '-o', "$dir/main.o"
	            ])
			}
			else {
				throw new RuntimeException("TODO: build $platform from $buildPlatform")
			}
			return			
		}
		
		if(platform.equals('darwin')) {
			if(buildPlatform.equals('darwin')) {
				runCommand([
					'g++', "-I$JAVA_HOME/include", '-D_JNI_IMPLEMENTATION_',
					'-c', "$dir/main.cpp",
					'-o', "$dir/main.o"
				])
			}
			else {
				throw new RuntimeException("TODO: build $platform from $buildPlatform")
			}
			return			
		}
	}

	private void copyExplodedDependents() {
		project.copy {
			project.configurations.compile.findAll{it.name.endsWith('.jar') && !it.name.contains('proguard')}.each {
				from tree(it)
			}
			project.libsDir.listFiles().each {
				from tree(it)
			}
			into stage1
		}

		File awt = project.file("$stage1/org/eclipse/swt/awt")
		if(awt.exists()) {
			runCommand(['rm', '-rf', awt])
		}
	}

	private void copyResources() {
		project.copy {
			from project.fileTree('src/main/java', { excludes: ["**/*.java", "**/*.class"] })
			from project.fileTree('resources', {})
			into stage1
		}
	}

	private void createExe(File dir) {
		if(platform.equals('windows') && buildPlatform.equals('linux')) {
			setupPlatformFiles()
		}

		def lib = project.file("$avian/libavian.a")
		logger.lifecycle "expanding $lib"
		if(platform.equals('windows')) {
			if(buildPlatform.equals('linux')) {
				def arx = arch.equals('i386') ? ['x86_64-w64-mingw32-ar', 'x', '--target=pe-i386'] : ['x86_64-w64-mingw32-ar', 'x']
				runCommand(arx + lib, dir)
			}
			else {
				throw new RuntimeException("TODO: build $platform from $buildPlatform")
			}
		} else {
			runCommand(['ar', 'x', lib], dir)
		}
		
		logger.lifecycle "building $dir/boot-jar.o"
		runCommand([
			"$avian/binaryToObject", "$dir/boot.jar", "$dir/boot-jar.o",
			'_binary_boot_jar_start', '_binary_boot_jar_end',
			platform, arch
		])

		compileMain(dir)

		switch(platform) {
		case 'linux':   createNixExe(dir); break;
		case 'darwin':  createNixExe(dir); break;
		case 'windows': createWinExe(dir); break;
		}
	}
	
	private void createFatJar() {
		copyExplodedDependents()
		copyResources()
		runCommand(['jar', 'u0f', 'boot.jar', stage1.list()].flatten(), stage1)
	}

	private void createNixExe(dir) {
		String outFile = "$output/gui-$version"
		logger.lifecycle "building executable: $outFile"
		output.mkdirs()

		def objs = project.fileTree(dir, { include '*.o' }).getFiles()

		def linkCmd = ['g++', '-rdynamic', '-ldl', '-lpthread', '-lz'] + objs + ['-o', outFile]
		if(buildPlatform.equals('darwin')) {
			linkCmd += ['-framework', 'CoreServices', '-framework', 'CoreFoundation']
		}
		
		runCommand(linkCmd)
	}
	
	private void createWinExe(dir) {
		String outFile = "$output/gui-${version}.exe"
		logger.lifecycle "building executable: $outFile"
		output.mkdirs()

		def objs = project.fileTree(dir, { include '**/*.o' }).getFiles()
		def defFile = project.file("build/gui-${version}.def")
		def expFile = project.file("build/gui-${version}.exp")
		
		if(buildPlatform.equals('windows')) {
			if(platformResources.exists()) {
				runCommand(['restool', "$platformResources/icon.rc", '-O', 'coff', '-o ', "$platformResources/icon.res"])
			}
			runCommand(['dlltool', '-z', defFile] + objs)
			runCommand(['dlltool', '-d', defFile, '-e', expFile])
			runCommand(['g++', '-rdynamic', '-ldl', '-lpthread', '-lz'] + objs + ['-o', outFile])
		}
		else {
			def resTool = project.arch.equals('i386') ? ['x86_64-w64-mingw32-windres', '--target', 'pe-i386'] : ['x86_64-w64-mingw32-windres']
			def dllTool = project.arch.equals('i386') ? ['x86_64-w64-mingw32-dlltool', '-mi386', '--as-flags=--32'] : ['x86_64-w64-mingw32-dlltool']
			def lnkTool = project.arch.equals('i386') ? ['x86_64-w64-mingw32-gcc', '-m32', '-march=i586'] : ['x86_64-w64-mingw32-gcc']

			if(platformResources.exists()) {
				runCommand(resTool + ["$platformResources/icon.rc", '-O', 'coff', '-o', "$platformResources/icon.res"])
			}
			runCommand(dllTool + ['-z', defFile] + objs)
			runCommand(dllTool + ['-d', defFile, '-e', expFile])

			runCommand(
				lnkTool + expFile + objs + [
				"-L$platformFiles/lib",
				'-lz', '-lm', '-lole32', '-lws2_32', '-lurlmon', '-luuid', '-Wl,--kill-at', '-mwindows',
				'-o', outFile]
			)
		}
	}

	private void createWin32ExeFromLinux(dir) {
		/**
		 * 
		 * DEAD CODE
		 * 
		 */

		String out = "$output/gui-${version}.exe"
		logger.lifecycle "building executable: $out"
		output.mkdirs()

		if(platformResources.exists()) {
			runCommand([
				'x86_64-w64-mingw32-windres', '--target', 'pe-i386',
				"$platformResources/icon.rc", '-O', 'coff', '-o', "$platformResources/icon.res"
			])
		}

		
		def defFile = project.file("build/gui-${version}.def")
		def expFile = project.file("build/gui-${version}.exp")
		
		def dllCmd = [ 'x86_64-w64-mingw32-dlltool', '-mi386', '--as-flags=--32', '-z', defFile ]
		project.fileTree(vmFiles, { include '*.o' }).each { file ->
			dllCmd << file
		}
		project.fileTree(dir, { include '*.o' }).each { file ->
			dllCmd << file
		}
		runCommand(dllCmd)

		
		runCommand([ 'x86_64-w64-mingw32-dlltool', '-mi386', '--as-flags=--32', '-d', defFile, '-e', expFile ])

				
		def lnkCmd = [
			'x86_64-w64-mingw32-gcc', '-m32', '-march=i586', 
			expFile
		]
		project.fileTree(vmFiles, { include '*.o' }).each { file ->
			lnkCmd << file
		}
		project.fileTree(dir, { include '*.o' }).each { file ->
			lnkCmd << file
		}
		lnkCmd << "-L$platformFiles/lib"
		lnkCmd << '-lz' << '-lm' << '-lole32' << '-lws2_32' << '-lurlmon' << '-luuid'
		lnkCmd << '-Wl,--kill-at' << '-mwindows'
		lnkCmd << '-o' << out
		runCommand(lnkCmd)
	}

	@TaskAction
	protected void run() {
		if(!library) {
			createFatJar()
//			if(proguard) {
//				runProguard()
//				createExe(stage2)
//			} else {
				createExe(stage1)
//			}
		}
	}
	
	private void runProguard() {
		stage1.mkdirs()
		stage2.mkdirs()

		File proguardJar = project.configurations.compile.findAll{it.name.contains('proguard') && it.name.endsWith('.jar')}.first()
		def include = [];
		include = project.configurations.compile.each {
			include << tree(it).findAll { it.name.endsWith('.pro') }
		}

		runCommand([
			'java', '-jar', proguardJar.getPath(),
			'-injars', "$stage1/boot.jar",
			'-outjars', "$stage2/boot.jar",
			'-printmapping', project.file('mapping.txt'),
			'-include', "$avian/vm.pro",
			'-include', project.file('gui.pro'),
			'-include', project.file('../rtaudio/src/main/c/audio.pro'),
			'-keep', "public class $mainClass { public static void main(java.lang.String[]); }"
		])
	}

	private void setupPlatformFiles() {
		String name = arch.equals('i386') ? 'win32' : 'win64'
		
		File dir = project.file("build/windows_resources")
		dir.mkdirs()
		project.copy {
			project.configurations.compile.findAll{it.name.contains(name) && it.name.endsWith('.tar.gz')}.each {
				from tree(it)
			}
			into dir
		}
		platformFiles = new File(dir, name)
		platformResources = project.file("build/windows_resources/$name/resources/windows")
	}
		
}
