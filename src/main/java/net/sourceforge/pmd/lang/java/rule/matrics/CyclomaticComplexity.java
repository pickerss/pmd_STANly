package net.sourceforge.pmd.lang.java.rule.matrics;

import java.util.List;
import java.util.Stack;

import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTBlockStatement;
import net.sourceforge.pmd.lang.java.ast.ASTCatchStatement;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTConditionalAndExpression;
import net.sourceforge.pmd.lang.java.ast.ASTConditionalExpression;
import net.sourceforge.pmd.lang.java.ast.ASTConditionalOrExpression;
import net.sourceforge.pmd.lang.java.ast.ASTConstructorDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTDoStatement;
import net.sourceforge.pmd.lang.java.ast.ASTEnumDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTExpression;
import net.sourceforge.pmd.lang.java.ast.ASTForStatement;
import net.sourceforge.pmd.lang.java.ast.ASTIfStatement;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclarator;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchLabel;
import net.sourceforge.pmd.lang.java.ast.ASTSwitchStatement;
import net.sourceforge.pmd.lang.java.ast.ASTWhileStatement;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;




public class CyclomaticComplexity extends AbstractJavaRule {
	private int reportLevel = 1;
	private boolean showClassesComplexity = true;
	private boolean showMethodsComplexity = true;
	
	private static class Entry {
		private Node node;
		private int decisionPoints = 1;
		public int highestDecisionPoints;
		public int methodCount;

		private Entry(Node node) {
			this.node = node;
		}

		public void bumpDecisionPoints() {
			decisionPoints++;
		}

		public void bumpDecisionPoints(int size) {
			decisionPoints += size;
		}

		//public int getComplexityAverage() {
			//return (double) methodCount == 0 ? 1 : (int) Math.rint( (double) decisionPoints / (double) methodCount );
		//}
		
		public double getComplexityAverage() {
			return (double) methodCount == 0 ? 1 : (double)( (double) decisionPoints / (double) methodCount );
		}
	}

	private Stack<Entry> entryStack = new Stack<Entry>();

	public static int sumExpressionComplexity(ASTExpression expr) {
		if (expr == null) {
			return 0;
		}

		List<ASTConditionalAndExpression> andNodes = expr.findDescendantsOfType(ASTConditionalAndExpression.class);
		List<ASTConditionalOrExpression> orNodes = expr.findDescendantsOfType(ASTConditionalOrExpression.class);

		int children = 0;

		for (ASTConditionalOrExpression element : orNodes) {
			children += element.jjtGetNumChildren();
			children--;
		}

		for (ASTConditionalAndExpression element : andNodes) {
			children += element.jjtGetNumChildren();
			children--;
		}

		return children;
	}

	@Override
	public Object visit(ASTCompilationUnit node, Object data) {
		
		return super.visit( node, data );
	}
	
	@Override
	public Object visit(ASTIfStatement node, Object data) {
		int boolCompIf = sumExpressionComplexity( node.getFirstChildOfType( ASTExpression.class ) );
		// If statement always has a complexity of at least 1
		boolCompIf++;

		entryStack.peek().bumpDecisionPoints( boolCompIf );
		super.visit( node, data );
		return data;
	}

	@Override
	public Object visit(ASTCatchStatement node, Object data) {
		entryStack.peek().bumpDecisionPoints();
		super.visit( node, data );
		return data;
	}

	@Override
	public Object visit(ASTForStatement node, Object data) {
		int boolCompFor = sumExpressionComplexity( node.getFirstDescendantOfType( ASTExpression.class ) );
		// For statement always has a complexity of at least 1
		boolCompFor++;

		entryStack.peek().bumpDecisionPoints( boolCompFor );
		super.visit( node, data );
		return data;
	}

	@Override
	public Object visit(ASTDoStatement node, Object data) {
		int boolCompDo = sumExpressionComplexity( node.getFirstChildOfType( ASTExpression.class ) );
		// Do statement always has a complexity of at least 1
		boolCompDo++;

		entryStack.peek().bumpDecisionPoints( boolCompDo );
		super.visit( node, data );
		return data;
	}

	@Override
	public Object visit(ASTSwitchStatement node, Object data) {
		Entry entry = entryStack.peek();

		int boolCompSwitch = sumExpressionComplexity( node.getFirstChildOfType( ASTExpression.class ) );
		entry.bumpDecisionPoints( boolCompSwitch );

		int childCount = node.jjtGetNumChildren();
		int lastIndex = childCount - 1;
		for ( int n = 0; n < lastIndex; n++ ) {
			Node childNode = node.jjtGetChild( n );
			if ( childNode instanceof ASTSwitchLabel ) {
				// default is generally not considered a decision (same as "else")
				ASTSwitchLabel sl = (ASTSwitchLabel) childNode;
				if ( !sl.isDefault() ) {
					childNode = node.jjtGetChild( n + 1 );
					if ( childNode instanceof ASTBlockStatement ) {
						entry.bumpDecisionPoints();
					}
				}
			}
		}
		super.visit( node, data );
		return data;
	}

	@Override
	public Object visit(ASTWhileStatement node, Object data) {
		int boolCompWhile = sumExpressionComplexity( node.getFirstChildOfType( ASTExpression.class ) );
		// While statement always has a complexity of at least 1
		boolCompWhile++;

		entryStack.peek().bumpDecisionPoints( boolCompWhile );
		super.visit( node, data );
		return data;
	}

	@Override
	public Object visit(ASTConditionalExpression node, Object data) {
		if ( node.isTernary() ) {
			int boolCompTern = sumExpressionComplexity( node.getFirstChildOfType( ASTExpression.class ) );
			// Ternary statement always has a complexity of at least 1
			boolCompTern++;

			entryStack.peek().bumpDecisionPoints( boolCompTern );
			super.visit( node, data );
		}
		return data;
	}

	@Override
	public Object visit(ASTClassOrInterfaceDeclaration node, Object data) {
		if ( node.isInterface() ) {
			return data;
		}

		entryStack.push( new Entry( node ) );
		super.visit( node, data );
		if ( showClassesComplexity ) {
			Entry classEntry = entryStack.pop();
			if ( classEntry.getComplexityAverage() >= reportLevel
					|| classEntry.highestDecisionPoints >= reportLevel ) {
				addViolation( data, node, new String[] {
						"class",
						node.getImage(),
						classEntry.getComplexityAverage() + " (Highest = "
						+ classEntry.highestDecisionPoints + ')' } );
			}
		}
		return data;
	}

	@Override
	public Object visit(ASTMethodDeclaration node, Object data) {
		entryStack.push( new Entry( node ) );
		super.visit( node, data );
		if ( showMethodsComplexity ) {
			Entry methodEntry = entryStack.pop();
			int methodDecisionPoints = methodEntry.decisionPoints;
			Entry classEntry = entryStack.peek();
			classEntry.methodCount++;
			classEntry.bumpDecisionPoints( methodDecisionPoints );

			if ( methodDecisionPoints > classEntry.highestDecisionPoints ) {
				classEntry.highestDecisionPoints = methodDecisionPoints;
			}
			ASTMethodDeclarator methodDeclarator = null;
			for ( int n = 0; n < node.jjtGetNumChildren(); n++ ) {
				Node childNode = node.jjtGetChild( n );
				if ( childNode instanceof ASTMethodDeclarator ) {
					methodDeclarator = (ASTMethodDeclarator) childNode;
					break;
				}
			}

			if ( methodEntry.decisionPoints >= reportLevel ) {
				addViolation( data, node, new String[] { "method",
						methodDeclarator == null ? "" : methodDeclarator.getImage(),
								String.valueOf( methodEntry.decisionPoints ) } );
			}
		}
		return data;
	}

	@Override
	public Object visit(ASTEnumDeclaration node, Object data) {
		entryStack.push( new Entry( node ) );
		super.visit( node, data );
		Entry classEntry = entryStack.pop();
		if ( classEntry.getComplexityAverage() >= reportLevel
				|| classEntry.highestDecisionPoints >= reportLevel ) {
			addViolation( data, node, new String[] {
					"class",
					node.getImage(),
					classEntry.getComplexityAverage() + "(Highest = "
							+ classEntry.highestDecisionPoints + ')' } );
		}
		return data;
	}

	@Override
	public Object visit(ASTConstructorDeclaration node, Object data) {
		entryStack.push( new Entry( node ) );
		super.visit( node, data );
		Entry constructorEntry = entryStack.pop();
		int constructorDecisionPointCount = constructorEntry.decisionPoints;
		Entry classEntry = entryStack.peek();
		classEntry.methodCount++;
		classEntry.decisionPoints += constructorDecisionPointCount;
		if ( constructorDecisionPointCount > classEntry.highestDecisionPoints ) {
			classEntry.highestDecisionPoints = constructorDecisionPointCount;
		}
		if ( constructorEntry.decisionPoints >= reportLevel ) {
			addViolation( data, node, new String[] { "constructor",
					classEntry.node.getImage(),
					String.valueOf( constructorDecisionPointCount ) } );
		}
		return data;
	}

}
