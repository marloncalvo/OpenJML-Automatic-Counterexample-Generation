package openjml;

import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;
import openjml.Main.MethodInfo;
import org.jmlspecs.openjml.JmlTree;
import org.jmlspecs.openjml.JmlTree.JmlClassDecl;
import org.jmlspecs.openjml.JmlTree.JmlMethodDecl;
import org.jmlspecs.openjml.vistors.JmlTreeScanner;

public class MethodAggregator extends JmlTreeScanner {
  private final List<MethodInfo> methods;
  private final Stack<JmlClassDecl> currentClass;
  private String packageName = "";

  private MethodAggregator() {
    this.methods = new ArrayList<>();
    this.currentClass = new Stack<>();
  }

  public static List<MethodInfo> getMethodsInfo(JmlTree.JmlCompilationUnit tree) {
    MethodAggregator aggregator = new MethodAggregator();
    aggregator.init(tree);
    aggregator.scan(tree);

    return aggregator.methods;
  }

  private void init(JmlTree.JmlCompilationUnit tree) {
    if (tree.getPackageName() != null) {
      packageName = tree.getPackageName().toString();
    } else {
      packageName = "";
    }
  }

  @Override
  public void visitJmlClassDecl(JmlClassDecl tree) {
    currentClass.push(tree);
    super.visitClassDef(tree);
  }

  @Override
  public void visitJmlMethodDecl(JmlMethodDecl that) {
    String methodName = that.getName().toString();
    List<String> paramTypes = that
        .getParameters()
        .stream()
        .map(param -> param.getType().toString())
        .collect(Collectors.toList());

    List<String> paramNames = that
        .getParameters()
        .stream()
        .map(param -> param.getName().toString())
        .collect(Collectors.toList());

    MethodInfo methodInfo = new MethodInfo(
        packageName,
        currentClass.peek().getSimpleName().toString(),
        methodName,
        (JmlMethodDecl) that.clone(),
        currentClass.peek());

    methodInfo.getParamTypes().addAll(paramTypes);
    methodInfo.getParamNames().addAll(paramNames);
    methods.add(methodInfo);
  }
}
