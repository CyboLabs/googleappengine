// Copyright 2009 Google Inc. All Rights Reserved.

package com.google.appengine.tools.development;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspC;
import org.apache.jasper.compiler.AntCompiler;
import org.apache.jasper.compiler.Localizer;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class LocalJspC {

  public static void main (String[] args) throws JasperException {
    if (args.length == 0) {
      System.out.println(Localizer.getMessage("jspc.usage"));
    } else {
      JspC jspc = new JspC() {
        @Override
        public String getCompilerClassName() {
            return LocalCompiler.class.getName();
        }
      };
      jspc.setArgs(args);
      jspc.setCompiler("extJavac");
      jspc.setAddWebXmlMappings(true);
      jspc.execute();
    }
  }

  /** Very simple compiler for JSPc that is behaving like the ANT compiler, but
  * uses the Tools System Java compiler to speed compilation process.
  * Only the generated code for *.tag files is compiled by JSPc even with the "-compile" flag
  * not set.
  **/
  public static class LocalCompiler extends AntCompiler {

    static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    @Override
    protected void generateClass(String[] smap) {
      if (compiler == null) {
        throw new RuntimeException(
            "Cannot get the System Java Compiler. Please use a JDK, not a JRE.");
      }
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
      ArrayList<File> files = new ArrayList<File>();
      files.add(new File(ctxt.getServletJavaFileName()));
      List<String> optionList = new ArrayList<String>();
      optionList.addAll(Arrays.asList("-classpath", System.getProperty("java.class.path")));
      optionList.addAll(Arrays.asList("-encoding", ctxt.getOptions().getJavaEncoding()));
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(files);
      compiler.getTask(null, fileManager, null, optionList, null, compilationUnits).call();
    }
  }
}
