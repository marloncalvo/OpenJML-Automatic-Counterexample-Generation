package openjml;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.jmlspecs.openjml.IAPI;
import org.jmlspecs.openjml.JmlTree.JmlClassDecl;
import org.jmlspecs.openjml.JmlTree.JmlMethodDecl;
import org.jmlspecs.openjml.JmlTree.JmlVariableDecl;
import org.jmlspecs.openjml.JmlTreeUtils;
import org.jmlspecs.openjml.vistors.JmlTreeScanner;
import com.sun.tools.javac.tree.JCTree;

public class JMLTransformer extends JmlTreeScanner {

  private JmlClassDecl currentClass;
  private JmlMethodDecl toFind;
  private Context context;
  private JmlTreeUtils treeUtils;


  private JMLTransformer(JmlMethodDecl toFind) {
    this.toFind = toFind;
    context = OpenJMLAPI.getInstance().context();
    treeUtils = JmlTreeUtils.instance(context);
  }

  public static void transform(JCTree tree, JmlMethodDecl methodDecl) {
    JMLTransformer transformer = new JMLTransformer(methodDecl);
    transformer.scan(tree);
  }

  @Override
  public void visitJmlClassDecl(JmlClassDecl tree) {
    currentClass = tree;
    for (JCTree arg : tree.getMembers()) {
      if (arg instanceof JmlMethodDecl && arg == toFind) {
        visitJmlMethodDecl((JmlMethodDecl) arg);
      }
    }
  }

  @Override
  public void visitJmlMethodDecl(JmlMethodDecl that) {
    JCExpression sentinelExpression = treeUtils
        .makeEquality(-1,
            treeUtils.makeIntLiteral(-1, -123),
            treeUtils.makeIntLiteral(-1, -123));

    java.util.List<JCExpression> expressions = new ArrayList<>();

    {
      java.util.List<JCTree.JCVariableDecl> instanceVariables = currentClass
          .getMembers()
          .stream()
          .filter(member -> member instanceof JmlVariableDecl)
          .map(member -> (JmlVariableDecl) member)
          .filter(member -> member.getModifiers().toString().contains("public"))
          .collect(Collectors.toList());

      List<JCTree.JCVariableDecl> parameters = that.getParameters();

      for (JCTree.JCVariableDecl var : parameters) {
        generateVarExpressions(expressions, var, null);
      }

      for (JCTree.JCVariableDecl var : instanceVariables) {
        JCIdent thisIdent = treeUtils.factory.Ident("this");
        generateVarExpressions(expressions, var, thisIdent);
      }
    }

    JCExpression cond = treeUtils.makeAnd(-1, sentinelExpression, expressions.toArray(new JCExpression[0]));
    JCStatement empty = treeUtils.factory.Block(0, com.sun.tools.javac.util.List.nil());

    JCStatement statement = treeUtils
        .factory
        .If(cond,
            empty,
            null);

    List<JCStatement> statements = that.getBody().getStatements().prepend(statement);
    that.getBody().stats         = statements;
  }

  private void generateVarExpressions(
      java.util.List<JCExpression> expressions,
      JCTree.JCVariableDecl variableDecl,
      JCExpression selected) {
    if (selected == null) {
      JCIdent ident = treeUtils.factory.Ident(variableDecl.getName());
      ident.setType(Type.noType);
      JCExpression expression = treeUtils
          .makeEquality(-1,
              ident,
              ident);
      expressions.add(expression);
    } else {
      JCExpression thisAccess = treeUtils.makeSelect(-1, selected, variableDecl.getName());
        thisAccess.setType(Type.noType);
        JCExpression expression = treeUtils
            .makeEquality(-1,
                thisAccess,
                thisAccess);
        expressions.add(expression);
    }
  }

}
