/*
 *  Copyright (C) 2011 Stephane Godbillon
 *  
 *  This file is part of BytecodeParser. See the README file in the root
 *  directory of this project.
 *
 *  BytecodeParser is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.

 *  BytecodeParser is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.

 *  You should have received a copy of the GNU Lesser General Public License
 *  along with BytecodeParser.  If not, see <http://www.gnu.org/licenses/>.
 */
package bytecodeparser.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import javassist.CtBehavior;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;
import bytecodeparser.utils.Utils;

/**
 * A local variable.
 * @author Stephane Godbillon
 *
 */
public class LocalVariable {
	private static final Logger LOGGER = Logger.getLogger(LocalVariable.class);
	
	/**
	 * The declaring behavior of this variable.
	 */
	public final CtBehavior behavior;
	/**
	 * The name of this variable.
	 */
	public final String name;
	/**
	 * The index of this local variable in the LocalVariableTable attribute.
	 */
	public final int index;
	/**
	 * The type of this variable.
	 */
	public final LocalVariableType type;
	/**
	 * Is this local variable a parameter of this behavior?
	 */
	public final boolean isParameter;
	
	/**
	 * Gives the range the local variable is valid within.
	 */
	public int[] getValidityRange() {
		int[] result = new int[2];
		LocalVariableAttribute localVariableAttribute = Utils.getLocalVariableAttribute(behavior);
		result[0] = localVariableAttribute.startPc(index);
		result[1] = result[0] + localVariableAttribute.codeLength(index);
		return result;
	}
	/**
	 * The slot the local variable is put into.
	 * @return
	 */
	public int getSlot() {
		return Utils.getLocalVariableAttribute(behavior).index(index);
	}
	
	public LocalVariable(int index, String name, LocalVariableType type,  boolean isParameter, CtBehavior behavior) {
		this.index = index;
		this.name = name;
		this.type = type;
		this.behavior = behavior;
		this.isParameter = isParameter;
	}
	@Override
	public String toString() {
		return name + " (" + type.typeName + ") " + "[" + index + " -> " + getSlot() + "] between [" + getValidityRange()[0] + "," + getValidityRange()[1] + "]";
	}
	
	/**
	 * Gets all the local variables of the given behavior, indexed by their index in the LocalVariableTable attribute.
	 * @param behavior
	 * @return the local variables of the given behavior, indexed by their index in the LocalVariableTable attribute.
	 * @throws NotFoundException
	 */
	public static Map<Integer, LocalVariable> findVariables(CtBehavior behavior) throws NotFoundException {
		int nbParameters = behavior.getParameterTypes().length;
		boolean isStatic = Modifier.isStatic(behavior.getModifiers());
		Map<Integer, LocalVariable> variables = new HashMap<Integer, LocalVariable>();
		CodeAttribute codeAttribute = behavior.getMethodInfo().getCodeAttribute();
		LocalVariableAttribute localVariableAttribute = (LocalVariableAttribute) codeAttribute.getAttribute("LocalVariableTable");
		LOGGER.debug("search vars : " + localVariableAttribute + " > " + (localVariableAttribute != null ? localVariableAttribute.tableLength() : 0));
		if(localVariableAttribute != null) {
			for(int i = 0; i < localVariableAttribute.tableLength(); i++) {
				boolean isParameter = isStatic ? i < nbParameters : (i > 0 && i <= nbParameters);
				LocalVariable localVariable = new LocalVariable(i, localVariableAttribute.variableName(i), LocalVariableType.parse(localVariableAttribute.signature(i)), isParameter, behavior);
				variables.put(i, localVariable);
				LOGGER.debug(String.format("findLocalVariables: foud var %s is '%s' (slot %s)", i, localVariable.name, localVariable.getSlot()));
			}
		} else LOGGER.debug("no local vars found");
		return variables;
	}
	
	/**
	 * Get the local variable in the given slot at the given index in the given map of local variables.
	 * @param slot
	 * @param index
	 * @param variables
	 * @return the local variable in the given slot at the given index.
	 */
	public static LocalVariable getLocalVariable(int slot, int index, Map<Integer, LocalVariable> variables) {
		TreeMap<Integer, LocalVariable> variablesByDistance = new TreeMap<Integer, LocalVariable>();
		for(LocalVariable lv : variables.values()) {
			if(lv.getSlot() == slot) {
				int[] validityRange = lv.getValidityRange();
				if(validityRange[1] >= index) {
					if(validityRange[0] <= index) {
						LOGGER.debug("getLocalVariable in slot " + slot + " at index " + index + ": found " + lv);
						return lv;
					} else
						variablesByDistance.put(validityRange[0] - index, lv);
				}
			}
		}
		if(variablesByDistance.size() > 0) {
			LOGGER.debug("getLocalVariable in slot " + slot + " at index " + index + ": found by shorter distance " + variablesByDistance.firstEntry().getValue());
			return variablesByDistance.firstEntry().getValue();
		}
		LOGGER.debug("getLocalVariable in slot " + slot + " at index " + index + ": NOT FOUND");
		return null;
	}
}