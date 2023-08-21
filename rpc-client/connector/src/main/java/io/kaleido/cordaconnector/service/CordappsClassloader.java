package io.kaleido.cordaconnector.service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This is NOT used as it doesn't work for loading cordapps from a folder, until the following error is solved:
// - Caused by: java.io.NotSerializableException: io.kaleido.samples.state.IOUState: Interface net.corda.core.contracts.LinearState requires a field named participants but that isn't found in the schema or any superclass schemas
// - net.corda.core.contracts.TransactionState  : State class io.kaleido.samples.state.IOUState is not annotated with @BelongsToContract, and does not have an enclosing class which implements Contract. Annotate IOUState with @BelongsToContract(io.kaleido.samples.contract.IOUContract.class) to remove this warning.
// Instead we are adding the cordapps directory to the classpath at launch time

// @Component
public class CordappsClassloader {
  private static final Logger logger = LoggerFactory.getLogger(CordappsClassloader.class);
  private URLClassLoader flowClassLoader;

  @PostConstruct
  public void init() {
    List<String> cordappJars = new ArrayList<>();
    String folder = "~/cordapp_libs";
    Stream<Path> paths = null;
    try {
      paths = Files.walk(Paths.get(folder));
      paths
          .filter(Files::isRegularFile)
          .forEach((Path file) -> {
            cordappJars.add(file.toString());
          });
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      logger.info("Found cordapp jars {}", cordappJars.toString());
      if (paths != null) {
        paths.close();
      }
    }

    try {
      ClassLoader parent = Thread.currentThread().getContextClassLoader();
      logger.info("Parent classloader: {}", parent.toString());
      this.flowClassLoader = new URLClassLoader(cordappJars.stream().map(jar -> {
        try {
          return new File(jar).toURI().toURL();
        } catch (Exception e) {
          e.printStackTrace();
          return null;
        }
      }).toArray(URL[]::new), parent);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public URLClassLoader getFlowClassLoader() {
    return this.flowClassLoader;
  }
}
