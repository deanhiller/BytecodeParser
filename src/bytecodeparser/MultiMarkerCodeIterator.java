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
package bytecodeparser;

import java.util.ArrayList;

import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;

/**
 * A code iterator that tracks more than one mark in the bytecode.
 * @author Stephane Godbillon
 *
 */
public class MultiMarkerCodeIterator extends CodeIterator {
	public ArrayList<Integer> marks = new ArrayList<Integer>();

	protected MultiMarkerCodeIterator(CodeAttribute ca) {
		super(ca);
	}

	@Override
	protected void updateCursors(int pos, int length) {
		super.updateCursors(pos, length);
		for(int i = 0; i < marks.size(); i++) {
			int mark = marks.get(i);
			if(mark > pos)
				marks.set(i, mark + length);
		}
	}
	
	/**
	 * Puts a mark on the given index in the bytecode.
	 * @param index
	 * @return the id of this mark, in order to get it later.
	 */
	public int putMark(int index) {
		marks.add(index);
		return marks.size() - 1;
	}
	
	/**
	 * Gets the index in the bytecode tracked by the mark matching the given mark id.
	 * @param mark the mark id.
	 * @return the index of this mark.
	 */
	public int getMark(int mark) {
		return marks.get(mark);
	}
}
