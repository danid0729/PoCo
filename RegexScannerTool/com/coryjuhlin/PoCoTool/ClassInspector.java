package com.coryjuhlin.PoCoTool;

import java.util.HashSet;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ClassInspector extends ClassVisitor {
	private HashSet<String> callList;
	private String className = null;

	public ClassInspector(HashSet<String> setToUse) {
		super(Opcodes.ASM4);
		callList = setToUse;
	}
	
	@Override
    public void visit(int version, int access, String name, String signature,
    		String superName, String[] interfaces) {
		className = name.replace('/', '.');
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc,
            String signature, String[] exceptions) {
		callList.add(createMethodName(className,name,desc));
		return null;
	}
	
	public static String createMethodName(String owner, String name, String desc) {
		/* desc is always of the form "(arglist)returntype" */
		final int START_ARG_LIST = 1;
		final int END_ARG_LIST = desc.indexOf(')');
		
		String returnType = generateTypeName(desc.substring(END_ARG_LIST + 1));
		
		StringBuilder methodName = new StringBuilder(returnType);
		methodName.append(' ');
		methodName.append(owner.replace('/', '.'));
		methodName.append('.');
		methodName.append(name);
		methodName.append('(');
		
		// Enumerate through all arguments
		int argListSize = END_ARG_LIST - START_ARG_LIST;
		while(argListSize > 0) {
			int startLocation = END_ARG_LIST - argListSize;
			
			// Skip through all array declarations to find type code
			int arrayDecOffset = startLocation;
			char code;
			while((code = desc.charAt(arrayDecOffset)) == '[') {
				arrayDecOffset++;
			}
			
			int endLocation = arrayDecOffset + 1;

			
			if(code == 'L') {
				// For Object names, the end is up to (and including) the semicolon
				endLocation = desc.indexOf(';', startLocation) + 1;
			}
			
			methodName.append(generateTypeName(desc.substring(startLocation, endLocation)));
			argListSize -= (endLocation - startLocation);
			
			// Add a comma for all but the final argument
			if(argListSize > 0) {
				methodName.append(", ");
			}
		}
		
		methodName.append(')');
		
		return methodName.toString();
	}
	
	public static String generateTypeName(String type) {
		int arrayDepth = 0;
		
		// Check if array, compute array dimension
		while(type.charAt(arrayDepth) == '[') {
			arrayDepth++;
		}
		
		// Get primitive/object type name
		String baseType = null;
		try {
			baseType = typeCodeToName(type.substring(arrayDepth));
		} catch (Exception e) {
			System.out.println();
			System.out.println("FATAL ERROR:");
			System.out.println(e.getMessage());
			System.exit(-1);
		}
		
		// Append array brackets if necessary
		StringBuilder typeName = new StringBuilder(baseType);
		while(arrayDepth > 0) {
			typeName.append("[]");
			arrayDepth--;
		}
		
		return typeName.toString();
	}
	
	public static String typeCodeToName(String typeCode) throws Exception {
		if(typeCode == null || typeCode.length() == 0) {
			return null;
		}
		
		char typeChar = typeCode.charAt(0);
		
		switch(typeChar) {
		case 'V':
			return "void";
		case 'I':
			return "int";
		case 'D':
			return "double";
		case 'Z':
			return "boolean";
		case 'C':
			return "char";
		case 'B':
			return "byte";
		case 'S':
			return "short";
		case 'F':
			return "float";
		case 'J':
			return "long";
		case 'L':
			return objectTypeName(typeCode);
		default:
			throw new Exception("Unknown type code: '" + typeChar + "'");
		}
	}
	
	public static String objectTypeName(String type) {
		if(type == null || type.length() == 0) {
			return null;
		}
		
		if(type.charAt(0) != 'L') {
			return null;
		}
		
		// Remove the starting 'L' and the final ';'
		return type.substring(1, type.length() - 1).replace('/', '.');
	}

}
