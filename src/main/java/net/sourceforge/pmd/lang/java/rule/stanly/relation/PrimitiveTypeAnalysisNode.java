package net.sourceforge.pmd.lang.java.rule.stanly.relation;

import java.util.Map;

import net.sourceforge.pmd.lang.java.ast.ASTPrimaryExpression;
import net.sourceforge.pmd.lang.java.ast.ASTPrimitiveType;
import net.sourceforge.pmd.lang.java.ast.AbstractJavaNode;
import net.sourceforge.pmd.lang.java.rule.stanly.DomainRelationList;
import net.sourceforge.pmd.lang.java.rule.stanly.element.ElementNode;

public class PrimitiveTypeAnalysisNode extends AbstractASTAnalysisNode {

	public PrimitiveTypeAnalysisNode(DomainRelationList relationlist,
			Map<ASTPrimaryExpression, MethodResult> PrimaryExpressionList,
			MethodAnlaysis anlysis) {
		super(relationlist, PrimaryExpressionList, anlysis);
		// TODO Auto-generated constructor stub
	}

	@Override
	public MethodResult AnalysisAST(AbstractJavaNode analysisnode,ElementNode sourcenode) throws MethodAnalysisException 
	{
		ASTPrimitiveType primitivetype = (ASTPrimitiveType)analysisnode;
		
		return new MethodResult("",primitivetype.getImage(),true);
	}

}
