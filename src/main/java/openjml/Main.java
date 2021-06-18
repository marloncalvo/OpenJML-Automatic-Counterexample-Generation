package openjml;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jmlspecs.openjml.Factory;
import org.jmlspecs.openjml.IAPI;
import org.jmlspecs.openjml.JmlTree;

import java.util.List;
import org.jmlspecs.openjml.JmlTree.JmlClassDecl;
import org.jmlspecs.openjml.JmlTree.JmlCompilationUnit;
import org.jmlspecs.openjml.JmlTree.JmlMethodDecl;
import org.jmlspecs.openjml.JmlTreeUtils;
import org.jmlspecs.openjml.vistors.JmlTreeScanner;

public class Main {

  private IAPI openJml;

  public Main() {
    try {
      openJml = Factory.makeAPI();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    String file = args[0];
    Main main = new Main();
    String testSuite = main.generateTestSuite(file);
    System.out.println("==================================== RESULT ====================================");
    System.out.println(testSuite);
  }

  public String generateTestSuite(String file) {
    TestSuite testSuite = constructTestSuite(file);
    addTestCases(testSuite, file);
    String out = createTemporaryFile(testSuite.getClassName(), testSuite.toString());
    JmlCompilationUnit compiled = compileCode(out);
    try {
      return openJml.prettyPrint(compiled);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public JmlTree.JmlCompilationUnit compileCode(String file) {
    openJml.close();
    try {
      openJml = Factory.makeAPI();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    JmlTree.JmlCompilationUnit compiled = openJml.parseSingleFile(file);
    return compiled;
  }

  /**
   * Construct Test Suite:
   *  1. Run OpenJML ESC and store each failing function
   *  2. Create driver method for each method and copy specs.
   *  3. Call RAC and extract methods.
   *  4. Remove ticks and "\".
   *  5. Create class and add each method in there.
   */

  private static class TestSuite {
    private final String pckg;
    private final String className;
    private final List<String> imports;
    private final List<String> testCases;
    private final List<String> driverMethods;

    public String getClassName() {
      return className;
    }

    public List<String> getImports() {
      return imports;
    }

    public List<String> getTestCases() {
      return testCases;
    }

    public List<String> getDriverMethods() {
      return driverMethods;
    }

    public TestSuite(String className, String pckg) {
      this.className = className;
      this.pckg = pckg;
      this.imports = new ArrayList<>();
      this.testCases = new ArrayList<>();
      this.driverMethods = new ArrayList<>();
    }

    public String toString() {
      StringBuilder res = new StringBuilder();
      if (!pckg.isEmpty()) res.append("package " + pckg + ";\n");
      imports.forEach(imprt -> res.append(imprt + "\n"));
      res.append("public class " + className + "Test {\n");
      res.append("\n");
      testCases.forEach(test -> res.append(test + "\n"));
      res.append("\n");
      driverMethods.forEach(method -> res.append(method + "\n"));
      res.append("\n}\n");

      return res.toString();
    }
  }

  private static class MethodFinder extends JmlTreeScanner {
    private JmlMethodDecl method;
    private MethodInfo info;
    private boolean extra = true;

    private MethodFinder(MethodInfo info) {
      this.info = info;
      this.method = null;
    }

    public static JmlMethodDecl find(MethodInfo info, JCTree tree) {
      MethodFinder finder = new MethodFinder(info);
      finder.scan(tree);

      return finder.method;
    }

    public static JmlMethodDecl find(MethodInfo info, JCTree tree, boolean extra) {
      MethodFinder finder = new MethodFinder(info);
      finder.extra = extra;
      finder.scan(tree);

      return finder.method;
    }

    @Override
    public void visitJmlMethodDecl(JmlMethodDecl that) {
      if (that.getName().toString().equals(info.getMethodName())) {
        if (that.getParameters().size() == info.getParamTypes().size() + (extra ? 1 : 0)) {
          for (int i = 0; i < info.getParamTypes().size(); i++) {
            if (!that.getParameters().get(i+ (extra ? 1 : 0)).getType().toString().equals(info.getParamTypes().get(i))) {
              return;
            }
          }
          method = that;
        }
      }
    }
  }

  public TestSuite constructTestSuite(String file) {
    List<MethodInfo> methods = retrieveMethods(file);
    List<Pair<String, List<JCImport>>> racMethods = methods.stream().map(
        method -> getRacMethod(method, createDriver(method), file)
    ).collect(Collectors.toList());
    racMethods.forEach(method -> filterMethod(method.getKey()));
    TestSuite testSuite = createTestSuiteClass(file, racMethods.stream().map(rac -> rac.getKey()).collect(
        Collectors.toList()));

    testSuite.getImports().addAll(racMethods.get(0).getValue().stream().map(impt -> impt.toString()).collect(
        Collectors.toList()));

    return testSuite;
  }

  public List<MethodInfo> retrieveMethods(String file) {
    JmlTree.JmlCompilationUnit tree = compileCode(file);
    return MethodAggregator.getMethodsInfo(tree);
  }

  public String createDriver(MethodInfo method) {
    JmlMethodDecl newMethod = (JmlMethodDecl) method.getMethodDecl().clone();
    JmlTreeUtils utils = JmlTreeUtils.instance(openJml.context());

    JCVariableDecl instanceVariable = utils.makeVarDef(null, utils.factory.Name("instance"), null, -1);
    newMethod.params = newMethod.getParameters().prepend(instanceVariable);
    com.sun.tools.javac.util.List<JCExpression> args = com.sun.tools.javac.util.List.nil();
    for (int i = 1; i < newMethod.getParameters().size(); i++) {
      JCTree.JCVariableDecl arg = newMethod.getParameters().get(i);
      args = args.append(utils.factory.Ident(arg.getName().toString()));
    }

    JCExpression callDriverExpression = utils.factory.Select(
        utils.factory.Ident(instanceVariable),
        utils.factory.Name(method.getMethodName()));

    callDriverExpression.setType(Type.noType);

    // Still have to fix setting types of args maybe.
    newMethod.getBody().stats = com.sun.tools.javac.util.List.of(
        utils.factory.Return(
                utils.factory.App(
                    callDriverExpression,
                    args
                )
            )
    );

    String result = String.format(""
        + "public class %sDriver {\n"
        + " %s"
        + "}",
        method.getClassName(),
        newMethod.toString().replaceAll("this\\.","instance.")).replace("/*missing*/", method.getClassName());

    if (!method.getPackageName().isEmpty()) {
      result = "package " + method.getPackageName() + ";\n" + result;
    }

    return result;
  }

  public Pair<String, List<JCImport>> getRacMethod(MethodInfo info, String driver, String originalFile) {
    String path = createTemporaryFile(info.getClassName() + "Driver", driver);
    String result = runOpenJML("-rac","-show",path,originalFile);

    result = result.substring(result.indexOf("[jmlrac] RAC Transformed: "));
    Scanner scan = new Scanner(result);
    StringBuilder builder = new StringBuilder();
    scan.nextLine();
    while(scan.hasNextLine()) {
      String line = scan.nextLine();
      if (line.contains("[jmlrac]")) break;
      if (line.trim().startsWith("//")) continue;
      line = line.replaceAll("`","");
      line = line.replaceAll("\\\\","");
      builder.append(line);
      builder.append("\n");
    }

    String sourceCode = builder.toString().replace("signals () false;", "");
    if (!info.getPackageName().isEmpty()) {
      sourceCode = sourceCode.replace("package " + info.getPackageName() + ";", "");
      sourceCode = "package " + info.getPackageName() + ";\n" + sourceCode;
    }

    String tempPath = createTemporaryFile(info.getClassName() + "Driver", sourceCode);
    JmlTree.JmlCompilationUnit compiled = compileCode(tempPath);

    return new ImmutablePair<>(MethodFinder.find(info, compiled).toString(), compiled.getImports());
  }

  public void filterMethod(String method) {
    method.replaceAll("(`|\\\\)", "");
  }

  public TestSuite createTestSuiteClass(String file, List<String> methods) {
    JmlTree.JmlCompilationUnit compiled = compileCode(file);
    String testClassName = new File(file).getName().replace(".java", "");
    List<String> imports = compiled
        .getImports()
        .stream()
        .map(jcImport -> jcImport.toString())
        .collect(Collectors.toList());

    String packageName = compiled.getPackageName() == null ? "" : compiled.getPackageName().toString();

    TestSuite testSuite = new TestSuite(testClassName, packageName);
    testSuite.getImports().addAll(imports);
    testSuite.getDriverMethods().addAll(methods);

    return testSuite;
  }

  /**
   * Construct Counterexample Inputs:
   *  * 1. Add if(...) to each method of file.
   *  * 2. Run OpenJML ESC subexpressions and store each counterexample
   *  * 3. Extract if(...) values from each counterexample
   *  * 4. For each counterexample, create method which:
   *    * a. Calls construct() for instance.
   *    * b. Calls construct() for each input argument.
   *    * c. Calls function with each input argument.
   */
  public static class MethodInfo {
    private final String packageName;
    private final String className;
    private final String methodName;
    private final List<String> paramTypes;
    private final List<String> paramNames;
    private final JmlMethodDecl methodDecl;
    private final JmlClassDecl classDecl;

    public MethodInfo(String packageName, String className, String methodName, JmlMethodDecl methodDecl, JmlClassDecl classDecl) {
      this.packageName = packageName;
      this.className = className;
      this.methodName = methodName;
      this.methodDecl = methodDecl;
      this.classDecl = classDecl;
      this.paramTypes = new ArrayList<>();
      this.paramNames = new ArrayList<>();
    }

    public String getPackageName() {
      return packageName;
    }

    public String getClassName() {
      return className;
    }

    public String getMethodName() {
      return methodName;
    }

    public JmlMethodDecl getMethodDecl() {
      return methodDecl;
    }

    public List<String> getParamTypes() {
      return paramTypes;
    }

    public List<String> getParamNames() {
      return paramNames;
    }

    public JmlClassDecl getClassDecl() {
      return classDecl;
    }
  }

  private static class ObjectTree {
    private final String identifier;
    private final String value;
    private final List<ObjectTree> children;

    public ObjectTree(String identifier, String value) {
      this.identifier = identifier;
      this.value = value;
      this.children = new ArrayList<>();
    }

    public String getIdentifier() {
      return identifier;
    }

    public String getValue() {
      return value;
    }

    public List<ObjectTree> getChildren() {
      return children;
    }
  }

  public void addTestCases(TestSuite testSuite, String file) {
    List<MethodInfo> methods = retrieveMethods(file);

    // Store counterexamples for each method. Note that there can be
    // multiple counterexamples per method.
    List<List<String>> counterexamplesText = methods.stream().map(
        method -> {
          JmlTree.JmlCompilationUnit classTree = compileCode(file);
          JmlMethodDecl methodTree = getMethodTree(method, classTree);
          supplementMethod(classTree, methodTree);
          String sourceCode = createTemporaryFile(method.getClassName(), classTree);
          return getCounterExampleText(method,sourceCode);
        }
    ).collect(Collectors.toList());

    // For each method, for each counterexample, all the variable values
    List<List<List<Pair<String, String>>>> counterexamples = counterexamplesText
        .stream()
        .map(method -> method
            .stream()
            .map(counterexample -> getCounterExampleValues(counterexample))
            .collect(Collectors.toList())
    ).collect(Collectors.toList());

    List<Object> testCases = new ArrayList<>();
    AtomicInteger integer = new AtomicInteger(0);
    for (int i = 0; i < methods.size(); i++) {
      MethodInfo method = methods.get(i);
      List<List<Pair<String, String>>> counterexampless = counterexamples.get(i);
      testCases.addAll(counterexampless
          .stream()
          .map(counterexample -> createTestCase(counterexample,method,integer.getAndIncrement()))
          .collect(Collectors.toList()));
    }

    testSuite
        .getTestCases()
        .addAll(testCases.stream().map(
            testCase -> testCase.toString()).collect(Collectors.toList()));
  }

  public String createTemporaryFile(String fileName, Object sourceCode) {
    try {
      Path tempDir = Files.createTempDirectory("jml_files");
      File file = tempDir.resolve(fileName + ".java").toFile();
      file.createNewFile();
      PrintWriter writer = new PrintWriter(file);
      writer.println(sourceCode);
      writer.close();
      return file.getAbsolutePath();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public JmlMethodDecl getMethodTree(MethodInfo methodInfo, JmlTree.JmlCompilationUnit classTree) {
    return MethodFinder.find(methodInfo, classTree, false);
  }

  public void supplementMethod(JCTree tree, JmlMethodDecl method) {
    JMLTransformer.transform(tree, method);
  }

  public List<String> getCounterExampleText(MethodInfo method, String file) {
    String methodToUse = method.getPackageName()
        + "."
        + method.getClassName()
        + "."
        + method.getMethodName()
        + "("
        + StringUtils.join(method.getParamTypes(), ",")
        + ")";

    if (methodToUse.startsWith(".")) {
      methodToUse = methodToUse.substring(1);
    }

    String result = runOpenJML("-esc","-method",methodToUse,"-subexpressions",file);
    Scanner scan = new Scanner(result);
    boolean found = false;
    List<String> counterexamples = new ArrayList<>();
    String currentString = "";
    while (scan.hasNextLine()) {
      String line = scan.nextLine();
      if (line.contains("TRACE of") && !found) {
        found = true;
        continue;
      } else if (line.trim().isEmpty() && found) {
        found = false;
        counterexamples.add(currentString);
      } else if (found) {
        currentString += line + "\n";
      }
    }

    return counterexamples;
  }

  public List<Pair<String, String>> getCounterExampleValues(String counterexample) {
    try {
      int beginIndex = counterexample.indexOf("if (-123 == -123 &&");
      counterexample = counterexample.substring(beginIndex);
      counterexample = counterexample.substring(counterexample.indexOf("\n"));
      counterexample = counterexample.substring(0, counterexample.indexOf("Condition = true"));
      counterexample = counterexample.trim().replaceAll("\t", "");
      Scanner scanner = new Scanner(counterexample);
      scanner.nextLine();
      scanner.nextLine();
      scanner.nextLine();
      List<Pair<String, String>> result = new ArrayList<>();
      while (scanner.hasNextLine()) {
        String equiv = scanner.nextLine();
        if (!scanner.hasNextLine()) break;
        scanner.nextLine();
        if (!scanner.hasNextLine()) break;
        scanner.nextLine();
        if (!scanner.hasNextLine()) break;
        scanner.nextLine();

        String[] values = equiv.split("===");
        values[0] = values[0].trim().replace("VALUE: ", "");
        values[1] = values[1].trim().replace("(","").replace(")","");
        result.add(new ImmutablePair<>(values[0], values[1]));
      }
      return result;
    } catch (Exception e) {
      return null;
    }
  }

  public String createTestCase(List<Pair<String, String>> values, MethodInfo methodInfo, int index) {
    String method = "public void test" + index + "() {\n"
        + " " + methodInfo.getClassName() + " instance = new " + methodInfo.getClassName() + "();\n";

    for (Pair<String, String> value : values) {
      if (value.getKey().contains("this")) {
        method += "\t" + value.getKey().replace("this","instance") + " = " + value.getValue() + ";\n";
      } else {
        int paramIndex = methodInfo.getParamNames().indexOf(value.getKey());
        String type = methodInfo.getParamTypes().get(paramIndex);
        method += "\t" + type + " " + value.getKey() + " = " + value.getValue() + ";\n";
      }
    }

    method += "\t" + methodInfo.getMethodName()
        + "(instance"
        + (methodInfo.getParamNames().size() > 0 ? "," : "")
        + StringUtils.join(methodInfo.getParamNames(), ",")
        + ");\n";
    method += "}";

    return method;
  }

  private String runOpenJML(String...args) {
    ProcessBuilder processBuilder = new ProcessBuilder(
        ArrayUtils.addAll(
          new String[]{"java","-jar","D:\\Tools\\OpenJML\\openjml.jar"},
          args
    ));

    String result = "";
    try {
      Process p = processBuilder.start();
      boolean stop = false;
      int offset = 0;
      StringBuilder builder = new StringBuilder();
      while(!stop) {
        Thread.sleep(500);
        try {
          stop = p.waitFor(500, TimeUnit.MILLISECONDS);
          InputStreamReader reader = new InputStreamReader(p.getInputStream());
          char[]buf = new char[256];
          int read = 0;
          while((read = reader.read(buf,offset,buf.length))!=-1){
            for (int i = 0; i < read; i++) {
              builder.append(buf[i]);
            }
          }
        } catch (InterruptedException e) {

        }
      }

      result = builder.toString();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }

    return result;
  }

}
