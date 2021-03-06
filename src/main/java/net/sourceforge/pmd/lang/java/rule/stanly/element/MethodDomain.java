package net.sourceforge.pmd.lang.java.rule.stanly.element;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pmd.lang.java.rule.stanly.metrics.MethodMetric;		


public class MethodDomain extends ElementNode {
	public MethodMetric metric;
	public List<String> parameters;
	public String returntype;
	public MethodDomain(ElementNode parent, ElementNodeType type, String name) {
		super(parent,type, name);
		// TODO Auto-generated constructor stub
		metric = new MethodMetric();
		parameters = new ArrayList<String>();
		returntype = "unknown";
	}
	public MethodDomain(ElementNodeType type, String name) {
		super(type, name);
		// TODO Auto-generated constructor stub
		metric = new MethodMetric();
		parameters = new ArrayList<String>();
		returntype = "unknown";
	}
	
	public String getMethodFullName()
	{
		String fullname = super.getFullName();
		
		fullname += "(";
		for(int i = 0 ; i< parameters.size(); i++)
		{
			fullname += parameters.get(i);
			fullname += ",";
		}
		if(fullname.charAt(fullname.length()-1) == ',')
			fullname = fullname.substring(0, fullname.length()-1);
		
		fullname += ")";
		
		return fullname;
	}

}
