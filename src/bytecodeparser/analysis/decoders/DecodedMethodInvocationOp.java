/**
 * 
 */
package bytecodeparser.analysis.decoders;

import static bytecodeparser.analysis.stack.Stack.StackElementLength.DOUBLE;
import static bytecodeparser.analysis.stack.Stack.StackElementLength.ONE;

import java.util.Arrays;
import java.util.Iterator;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import bytecodeparser.Context;
import bytecodeparser.analysis.LocalVariable;
import bytecodeparser.analysis.LocalVariableType;
import bytecodeparser.analysis.opcodes.MethodInvocationOpcode;
import bytecodeparser.analysis.stack.Stack;
import bytecodeparser.analysis.stack.Stack.StackElementLength;
import bytecodeparser.analysis.stack.StackAnalyzer.Frame;
import bytecodeparser.analysis.stack.StackElement;
import bytecodeparser.analysis.stack.TOP;
import bytecodeparser.analysis.stack.TrackableArray;
import bytecodeparser.analysis.stack.ValueFromLocalVariable;
import bytecodeparser.analysis.stack.Whatever;
import bytecodeparser.utils.Utils;

public class DecodedMethodInvocationOp extends DecodedOp {
	protected int nbParameters;
	protected String descriptor;
	protected CtClass[] parameterTypes;
	protected String declaringClassName;
	protected String name;
	
	protected StackElementLength[] pops;
	protected StackElementLength returnType;
	
	public DecodedMethodInvocationOp(MethodInvocationOpcode mop, Context context, int index) throws NotFoundException {
		super(mop, context, index);
		ConstPool constPool = Utils.getConstPool(context.behavior);
		boolean interfaceMethod = constPool.getTag(getMethodRefIndex()) == ConstPool.CONST_InterfaceMethodref;
		descriptor = interfaceMethod ? constPool.getInterfaceMethodrefType(getMethodRefIndex()) : constPool.getMethodrefType(getMethodRefIndex());
		name = interfaceMethod ? constPool.getInterfaceMethodrefName(getMethodRefIndex()) : constPool.getMethodrefName(getMethodRefIndex());
		declaringClassName = context.behavior.getDeclaringClass().getName();
		ClassPool cp = context.behavior.getDeclaringClass().getClassPool();
		parameterTypes = Descriptor.getParameterTypes(descriptor, cp);
		nbParameters = parameterTypes.length;
		StackElementLength[] pops = new StackElementLength[parameterTypes.length];
		for(int i = parameterTypes.length - 1, j = 0; i >= 0; i--, j++) {
			CtClass ctClass = parameterTypes[i];
			if(ctClass.isPrimitive()) {
				char d = ((CtPrimitiveType) ctClass).getDescriptor();
				if(d == 'J' || d == 'D') {
					pops[j] = DOUBLE;
				} else {
					pops[j] = ONE;
				}
			}
		}
		this.pops = pops;
		CtClass returnType = Descriptor.getReturnType(descriptor, cp);
		StackElementLength returnTypeLength = ONE;
		if(returnType.isPrimitive()) {
			char d = ((CtPrimitiveType) returnType).getDescriptor();
			if(d == 'V') {
				returnTypeLength = null;
			}
			if(d == 'J' || d == 'D') {
				returnTypeLength = DOUBLE;
			}
		}
		this.returnType = returnTypeLength != null ? returnTypeLength : null;
	}
	
	@Override
	public void simulate(Stack stack) {
		for(int i = 0; i < pops.length; i++) {
			if(pops[i] == DOUBLE)
				stack.pop2();
			else stack.pop();
		}
		if(op.as(MethodInvocationOpcode.class).isInstanceMethod())
			stack.pop();
		if(returnType != null) {
			if(returnType == DOUBLE)
				stack.push2(new Whatever());
			else stack.push(new Whatever());
		}
	}
	
	public int getMethodRefIndex() {
		return parameterValues[0];
	}
	public String getDescriptor() {
		return descriptor;
	}
	public String getName() {
		return name;
	}
	public String getDeclaringClassName() {
		return declaringClassName;
	}
	public CtClass[] getParameterTypes() {
		return parameterTypes;
	}
	public int getNbParameters() {
		return nbParameters;
	}
	public StackElementLength[] getPops() {
		return pops;
	}
	public StackElementLength[] getPushes() {
		return new StackElementLength[] { this.returnType };
	}
	
	public static MethodParams resolveParameters(Frame frame) {
		DecodedMethodInvocationOp decoded = (DecodedMethodInvocationOp) frame.decodedOp;
		int nbParams = decoded.getNbParameters();
		System.out.println("nbParams == " + nbParams + " for decoded " + decoded.name);
		MethodParam[] varargs = null;
		MethodParam[] params = resolveParameters(frame.stackBefore.stack, nbParams, false);
		if(nbParams > 0) {
			int stackIndex = 0;
			if(frame.stackBefore.stack.get(stackIndex) instanceof TOP)
				stackIndex = 1;
			if(frame.stackBefore.stack.get(stackIndex) instanceof TrackableArray) {
				TrackableArray trackableArray = (TrackableArray) frame.stackBefore.stack.get(stackIndex);
				varargs = resolveParameters(Arrays.asList(trackableArray.elements), trackableArray.elements.length, true);
				System.out.println("trackable array " + Arrays.toString(varargs) + " (" + trackableArray.elements.length + ")");
			}
		}
		return new MethodParams(params, varargs);
	}
	
	public static String[] resolveParametersNames(Frame frame, boolean varargs) {
		MethodParam[] params = varargs ? resolveParameters(frame).merge() : resolveParameters(frame).params;
		String[] result = new String[params.length];
		for(int i = 0; i < result.length; i++)
			result[i] = params[i].name;
		return result;
	}
	
	private static MethodParam[] resolveParameters(final Iterable<StackElement> stack, final int elements, boolean reverse) {
		MethodParam[] result = new MethodParam[elements];
		Iterator<StackElement> it = stack.iterator();
		int i = 0;
		while(it.hasNext() && i < elements) {
			StackElement se = it.next();
			if(se instanceof TOP)
				se = it.next();
			LocalVariable lv = getLocalVariableIfAvailable(se);
			if(lv != null) {
				result[reverse ? i : elements - i - 1] = new MethodParam(lv.name, lv.type);
			} else {
				result[reverse ? i : elements - i - 1] = new MethodParam(null, null);
			}
			i++;
		}
		return result;
	}
	
	private static LocalVariable getLocalVariableIfAvailable(StackElement se) {
		if(se instanceof ValueFromLocalVariable) {
			ValueFromLocalVariable v = (ValueFromLocalVariable) se;
			return v.localVariable;
		}
		return null;
	}
	
	public static class MethodParam {
		public final String name;
		public final LocalVariableType type;
		
		public MethodParam(String name, LocalVariableType type) {
			this.name = name;
			this.type = type;
		}
	}
	
	public static class MethodParams {
		public final MethodParam[] params;
		public final MethodParam[] varargs;
		
		public MethodParams(MethodParam[] params, MethodParam[] varargs) {
			this.params = params;
			this.varargs = varargs;
		}
		
		public MethodParam[] merge() {
			if(varargs == null)
				return Arrays.copyOf(params, params.length);
			MethodParam[] result = new MethodParam[params.length + varargs.length - 1];
			int i = 0;
			for(; i < params.length - 1; i++)
				result[i] = params[i];
			for(int j = 0; i < result.length; i++, j++)
				result[i] = varargs[j];
			return result;
		}
	}
}