package com.starlightuniverse;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

public class StarlightLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:5.1.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.mysql:mysql-connector-j:8.4.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.mindrot:jbcrypt:0.4"), null));
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
        ).build());
        classpathBuilder.addLibrary(resolver);
    }
}
