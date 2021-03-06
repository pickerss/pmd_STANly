package net.sourceforge.pmd.lang.java.rule.stanly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;

import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.lang.java.ast.*;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.rule.ViolationController;
import net.sourceforge.pmd.lang.java.rule.stanly.calculator.AbstractCalculator;
import net.sourceforge.pmd.lang.java.rule.stanly.calculator.CountMetrics;
import net.sourceforge.pmd.lang.java.rule.stanly.calculator.CouplingBetweenObjects;
import net.sourceforge.pmd.lang.java.rule.stanly.calculator.CyclomaticComplexity;
import net.sourceforge.pmd.lang.java.rule.stanly.calculator.LinesOfCode;
import net.sourceforge.pmd.lang.java.rule.stanly.element.ClassDomain;
import net.sourceforge.pmd.lang.java.rule.stanly.element.ElementNode;
import net.sourceforge.pmd.lang.java.rule.stanly.element.ElementNodeType;
import net.sourceforge.pmd.lang.java.rule.stanly.element.LibraryDomain;
import net.sourceforge.pmd.lang.java.rule.stanly.element.MethodDomain;
import net.sourceforge.pmd.lang.java.rule.stanly.element.PackageDomain;
import net.sourceforge.pmd.lang.java.rule.stanly.element.ProjectDomain;
import net.sourceforge.pmd.lang.java.rule.stanly.relation.MethodAnalysisException;
import net.sourceforge.pmd.lang.java.rule.stanly.relation.MethodAnlaysis;
import net.sourceforge.pmd.lang.java.rule.stanly.relation.MethodResult;

public class ProjectTree extends AbstractJavaRule {
	private List<AbstractCalculator> calculators = null;
	private static ProjectDomain projectNode = null;
	private static Stack<ElementNode> entryStack = new Stack<ElementNode>();
	private RelationManager manager = null;
	private String StartOfJavafile = null;
	// jdbc, hibernate
	
	public ProjectTree()
	{
		if(calculators == null)
		{
			calculators = new ArrayList<AbstractCalculator>();
			calculators.add(new LinesOfCode());
			calculators.add(new CountMetrics());
			calculators.add(new CyclomaticComplexity());
			calculators.add(new CouplingBetweenObjects());
			//calculators.add(new LackOfCohesiona());			
		}
		if(manager == null)
		{
			manager = new RelationManager();
		}
		if(projectNode == null)
		{
			projectNode = new ProjectDomain(ElementNodeType.PROJECT,"Project");
			StanlyControler.setRootNode(projectNode);
		}
	}
	
	public static ProjectDomain getProjectNode() {
		return projectNode;
	}
	
	public static Stack<ElementNode> getEntryStack() {
		return entryStack;
	}
	
	public Object visit(ASTCompilationUnit node, Object data)
	{	
		if(StartOfJavafile == null)
			StartOfJavafile = ((RuleContext)data).getSourceCodeFilename();
		else if(StartOfJavafile.equals(((RuleContext)data).getSourceCodeFilename()))
		{
			AfterRelations afterRelation = new AfterRelations(projectNode,manager);
			afterRelation.makePackageSet();
			afterRelation.analysisAnother();
			afterRelation.analysisunknown();
			System.out.println("metric 계산끝");
			
			StanlyControler.setRelationList(manager.getDomainRelationList());
			StanlyControler.setCompositionList(manager.CaculateDomainCompositionList());
			ViolationController.AfterCalculate();
		/*
			for(DomainComposition composition : StanlyControler.getCompositionList())
			{
				System.out.println("soruceID : " + composition.getSourceID());
				System.out.println("targetID : " + composition.getTargetID());
				System.out.println("count    : " + composition.getRelationCount());
				System.out.println("type     : " + composition.getDelegateType());
				System.out.println("");
			}
			*/
			return data;
		}
		
		LibraryDomain currentLibraryNode = null;
		PackageDomain currentPackageNode = null;
		
		String folderName = ((RuleContext)data).getSourceCodeFilename();
		String RootPath = StanlyControler.getRootPath();
		folderName = folderName.substring(RootPath.length()+1, folderName.length());
		ASTPackageDeclaration apd = node.getPackageDeclaration();
		char Sperate;
		
		String packageName;

		//OS에 따라
		if(System.getProperty("os.name").toLowerCase().contains("windows"))
			Sperate = '\\';
		else
			Sperate = '/';
		
		if(apd == null)	//패키지가 정의되지 않은경우에는 Package name을 <noname>로 설정함
		{
			packageName = "<noname>";
			folderName = folderName.substring(0, folderName.lastIndexOf(Sperate) + 1);
		}
		else
		{
			packageName = apd.getPackageNameImage();
			folderName = folderName.substring(0, folderName.indexOf(packageName.replace('.', Sperate)));
		}
		
		
		
		for(ElementNode pn : projectNode.getChildren())
		{
			if(pn.getName().equals(folderName))
			{
				currentLibraryNode = (LibraryDomain)pn;
				break;
			}
		}
		if(currentLibraryNode == null)
		{
			System.out.println("new folder node : " + folderName);
			currentLibraryNode = (LibraryDomain)projectNode.addChildren(ElementNodeType.LIBRARY, folderName);
		}
		
		for(ElementNode a : currentLibraryNode.getChildren())
		{
			if(a.getName().equals(packageName))
			{
				currentPackageNode = (PackageDomain)a;
				break;
			}
		}
		if(currentPackageNode == null)
		{
			System.out.println("    new package node : " + packageName);			
			currentPackageNode = (PackageDomain)currentLibraryNode.addChildren(ElementNodeType.PACKAGE,packageName);
		}
		
		entryStack.push(currentLibraryNode);
		entryStack.push(currentPackageNode);
		for(AbstractCalculator calculator: calculators)
			calculator.preCalcMetric(entryStack,node,data);
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		entryStack.pop();
		//System.out.println(currentPackageNode.metric.getCC() + "     " +currentPackageNode.metric.getAverageCC());
		//Gson gson = new Gson();
		//String temp = gson.toJson(topNode);
		////System.out.println(temp);
		return data;
	}
	
	public Object visit(ASTClassOrInterfaceDeclaration node, Object data)
	{
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		String name = node.getImage();
		if(node.isInterface())
		{
			thisNode = parent.addChildren(ElementNodeType.INTERFACE, name);
			((ClassDomain)thisNode).path = ((RuleContext)data).getSourceCodeFilename();
			//System.out.println("        new interface node : " + name);
		}
		else	
		{
			thisNode = parent.addChildren(ElementNodeType.CLASS, name);
			((ClassDomain)thisNode).path = ((RuleContext)data).getSourceCodeFilename();
			//System.out.println("        new class node : " + name);
		}
		manager.AddRelation(node, thisNode);
		
		entryStack.push(thisNode);
		for(AbstractCalculator calculator: calculators)
			calculator.preCalcMetric(entryStack,node,data);
		super.visit(node,data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		
		//System.out.println("        new class node : " + name + " " + ((ClassDomain)thisNode).metric.getLOC());
		return data;
	}
	
	public Object visit(ASTClassOrInterfaceBody node, Object data) // Instance Class
	{
		if(node.getNthParent(1) instanceof ASTAllocationExpression)
		{
			ElementNode thisNode;
			ElementNode parent = entryStack.peek();
			String name = node.getNthParent(1).getFirstChildOfType(ASTClassOrInterfaceType.class).getImage();
			
			thisNode = parent.addChildren(ElementNodeType.CLASS, name);
			((ClassDomain)thisNode).path = ((RuleContext)data).getSourceCodeFilename();
			//System.out.println("            new instace class node : " + name);
			
		
			entryStack.push(thisNode);
			super.visit(node,data);
			for(AbstractCalculator calculator: calculators)
				calculator.calcMetric(entryStack,node,data);
			entryStack.pop();
			return data;
		}
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return super.visit(node, data);
	}
	
	public Object visit(ASTEnumDeclaration node, Object data)
	{
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		String name = node.getImage();

		//System.out.println("        new enum node : " + name);
		
		
		thisNode = parent.addChildren(ElementNodeType.ENUM, name);
		((ClassDomain)thisNode).path = ((RuleContext)data).getSourceCodeFilename();
		
		entryStack.push(thisNode);
		super.visit(node,data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		
		return data;
	}
	
	public Object visit(ASTAnnotationTypeDeclaration node, Object data) {
		
		//node
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		String name = node.getImage();

		//System.out.println("        new enum node : " + name);
		
		
		thisNode = parent.addChildren(ElementNodeType.ANNOTATION, name);
		((ClassDomain)thisNode).path = ((RuleContext)data).getSourceCodeFilename();
		
		entryStack.push(thisNode);		
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		return data;
	}
	
	public Object visit(ASTFieldDeclaration node, Object data)
	{
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		List<ASTVariableDeclarator> varList = node.findChildrenOfType(ASTVariableDeclarator.class);
		for(ASTVariableDeclarator varNode:varList)
		{
			String name = varNode.getFirstChildOfType(ASTVariableDeclaratorId.class).getImage();
			thisNode = parent.addChildren(ElementNodeType.FIELD, name);
			manager.AddRelation(node, thisNode);
			//System.out.println("            new field node : " + name);

			entryStack.push(thisNode);
			super.visit(node, data);
			for(AbstractCalculator calculator: calculators)
				calculator.calcMetric(entryStack,node,data);
			entryStack.pop();
		}
		return data;
	}
	
	public String addTemplateParameters(String name,ASTTypeArguments template)
	{
		ASTTypeArguments recurseNode;
		ASTClassOrInterfaceType nameNode;
		name += '<';
		int n = template.jjtGetNumChildren();
		for(int i=0;i<n;i++)
		{
			nameNode = ((ASTTypeArgument)template.jjtGetChild(i)).getFirstDescendantOfType(ASTClassOrInterfaceType.class);
			if(nameNode != null)
			{
				name += nameNode.getImage();
				recurseNode = nameNode.getFirstDescendantOfType(ASTTypeArguments.class);
				if(recurseNode != null)
					name = addTemplateParameters(name,recurseNode);
				if(i != n-1)
					name += ',';
			}
		}
		name += '>';
		return name;
	}
	
	public void addParameters(MethodDomain enode, ASTFormalParameters parameters, Object data)
	{
		int n = parameters.getParameterCount();
		String parameterName;
		for(int i=0;i<n;i++)
		{
			ASTFormalParameter parameter = (ASTFormalParameter)parameters.jjtGetChild(i);
			parameterName = parameter.getTypeNode().getTypeImage();
			ASTTypeArguments template = parameter.getFirstDescendantOfType(ASTTypeArguments.class);
			if(template != null)
				parameterName = addTemplateParameters(parameterName,template);
			enode.parameters.add(parameterName);
			//System.out.println("                " + parameterName);
		}
	}
		
	public Object visit(ASTConstructorDeclaration node, Object data)
	{
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		String name = entryStack.peek().getName();
		
		thisNode = parent.addChildren(ElementNodeType.CONSTRUCTOR, name);
		//System.out.println("            new constructor node : " + name);
	
		
		addParameters((MethodDomain)thisNode,node.getParameters(),data);
		entryStack.push(thisNode);
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		
		return data;
	}
	
	
	
	public Object visit(ASTMethodDeclaration node, Object data)
	{
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		String name = node.getMethodName();
	
		//if(name.equals("tableModelFrom"))
		//	//System.out.println();
		thisNode = parent.addChildren(ElementNodeType.METHOD, name);
		//System.out.println("            new method node : " + name);
	
		//return type 받기
		MethodResult result = null;
		try
		{
			MethodAnlaysis analysis = new MethodAnlaysis(null);
			result = analysis.ProcessMethodCallAndAccess((AbstractJavaNode) node.getFirstChildOfType(ASTResultType.class), thisNode);
		}
		catch(MethodAnalysisException e)
		{
			e.PrintCauseException();
		}
		((MethodDomain)thisNode).returntype =  result.TypeName;
		
		addParameters((MethodDomain)thisNode,node.getFirstDescendantOfType(ASTFormalParameters.class),data);
		//if(thisNode.getFullName().equals("net.sourceforge.pmd.lang.java.rule.stanly.ProjectTree.visit"))
		//	System.out.println("");//YHC
		manager.AddRelation(node,thisNode);
		
		entryStack.push(thisNode);
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		
		return data;
	}
	public Object visit(ASTInitializer node, Object data) {
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		String name = "()";
		
		thisNode = parent.addChildren(ElementNodeType.METHOD, name);
		//System.out.println("            new initializer node : " + thisNode.getFullName());
		
		entryStack.push(thisNode);
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		
		
		return data;
	}
	
	
	public Object visit(ASTAnnotationMethodDeclaration node, Object data) {
		
		ElementNode thisNode;
		ElementNode parent = entryStack.peek();
		String name = node.getImage();

		thisNode = parent.addChildren(ElementNodeType.METHOD, name);
		
		//manager.AddRelation(node,thisNode);
		
		entryStack.push(thisNode);		
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		entryStack.pop();
		return data;
	}
	////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////

	public Object visit(ASTExtendsList node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	//public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
	//	return super.visit(node, data);
	//}
	
	
	public Object visit(ASTImplementsList node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTTypeParameters node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTMemberSelector node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTTypeParameter node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTTypeBound node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}	

	public Object visit(ASTClassOrInterfaceBodyDeclaration node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTEnumBody node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTEnumConstant node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTReferenceType node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		ElementNode thisNode = entryStack.peek();
		manager.AddRelation(node,thisNode);
		return data;
	}

	public Object visit(ASTClassOrInterfaceType node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTTypeArguments node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTTypeArgument node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTWildcardBounds node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTAnnotation node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTNormalAnnotation node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTMarkerAnnotation node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTSingleMemberAnnotation node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTMemberValuePairs node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTMemberValuePair node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTMemberValue node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTMemberValueArrayInitializer node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}
	
	public Object visit(ASTAnnotationTypeBody node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTAnnotationTypeMemberDeclaration node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}
	

	public Object visit(ASTDefaultValue node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTRUNSIGNEDSHIFT node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTRSIGNEDSHIFT node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	//public Object visit(ASTCompilationUnit node, Object data) {
		//return super.visit(node, data);
		//}

	//public Object visit(ASTEnumDeclaration node, Object data) {
		//return super.visit(node, data);
		//}
	
	public Object visit(ASTAssertStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPackageDeclaration node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTImportDeclaration node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTTypeDeclaration node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	//public Object visit(ASTFieldDeclaration node, Object data) {
	//return super.visit(node, data);
	//}

	public Object visit(ASTVariableDeclarator node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTVariableDeclaratorId node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTVariableInitializer node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTArrayInitializer node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	//public Object visit(ASTMethodDeclaration node, Object data) {
	//return super.visit(node, data);
	//}

	public Object visit(ASTMethodDeclarator node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTFormalParameters node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTFormalParameter node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	//public Object visit(ASTConstructorDeclaration node, Object data) {
	//return super.visit(node, data);
	//}

	public Object visit(ASTExplicitConstructorInvocation node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTType node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPrimitiveType node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTResultType node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTName node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTNameList node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTAssignmentOperator node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTConditionalExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTConditionalOrExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTConditionalAndExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTInclusiveOrExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTExclusiveOrExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTAndExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTEqualityExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTInstanceOfExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTRelationalExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTShiftExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTAdditiveExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTMultiplicativeExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTUnaryExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPreIncrementExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPreDecrementExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTUnaryExpressionNotPlusMinus node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPostfixExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTCastExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPrimaryExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPrimaryPrefix node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTPrimarySuffix node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTLiteral node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTBooleanLiteral node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTNullLiteral node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTArguments node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTArgumentList node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTAllocationExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTArrayDimsAndInits node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTLabeledStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTBlock node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTBlockStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTLocalVariableDeclaration node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTEmptyStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTStatementExpression node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTSwitchStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTSwitchLabel node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTIfStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTWhileStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTDoStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTForStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTForInit node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTStatementExpressionList node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTForUpdate node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTBreakStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTContinueStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTReturnStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTThrowStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTSynchronizedStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTTryStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTFinallyStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTCatchStatement node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTResourceSpecification node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTResources node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

	public Object visit(ASTResource node, Object data) {
		super.visit(node, data);
		for(AbstractCalculator calculator: calculators)
			calculator.calcMetric(entryStack,node,data);
		return data;
	}

}
