package org.luaj.vm2.lib;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import pl.shockah.shocky.Module;
import pl.shockah.shocky.Utils;
import pl.shockah.shocky.interfaces.IPaste;

public class BotLib extends TwoArgFunction {

	public LuaValue init(LuaValue env) {
		LuaTable t = (LuaTable) env.get("string");
		bind(t, BotLib.class, new String[] {"munge", "flip", "odd", "paste", "shorten"}, 1);
		return t;
	}
	
	public LuaValue call(LuaValue arg, LuaValue arg2) {
		if (opcode == 0)
			return init(arg2);
		String a = arg.checkjstring();
		String s = null;
		switch (opcode) { 
		case 1: s = Utils.mungeNick(a); break;
		case 2: s = Utils.flip(a); break;
		case 3: s = Utils.odd(a); break;
		case 4: {
				try {
					IPaste p = (IPaste) Module.getModule("paste");
					if (!(p==null)){
						s = p.paste(a);
					}
				} catch (Exception e){
					e.printStackTrace();
				}
			} break;
		case 5: s = Utils.shortenUrl(a); break;
		}
		return s == null ? NIL : valueOf(s);
	}
}