/* This file is part of Dynamic Surroundings, licensed under the MIT License (MIT).
 *
 * Copyright (c) OreCruncher
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.blockartistry.DynSurround.registry;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nonnull;

import org.blockartistry.DynSurround.data.xface.BiomeConfig;
import org.blockartistry.DynSurround.registry.RegistryManager.RegistryType;
import org.blockartistry.lib.script.BooleanValue;
import org.blockartistry.lib.script.Expression;
import org.blockartistry.lib.script.Variant;

import com.google.common.primitives.Booleans;

import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public abstract class BiomeMatcher {

	public abstract boolean match(@Nonnull final BiomeInfo info);

	public static BiomeMatcher getMatcher(@Nonnull final BiomeConfig cfg) {
		if (cfg.conditions != null)
			return new ConditionsImpl(cfg);
		return new LegacyImpl(RegistryManager.<BiomeRegistry>get(RegistryType.BIOME), cfg);
	}

	private static class LegacyImpl extends BiomeMatcher {

		protected final BiomeRegistry reg;
		protected final BiomeConfig config;

		public LegacyImpl(@Nonnull final BiomeRegistry reg, @Nonnull final BiomeConfig cfg) {
			this.reg = reg;
			this.config = cfg;
		}

		@Override
		public boolean match(@Nonnull final BiomeInfo info) {
			return this.reg.isBiomeMatch(this.config, info);
		}

	}

	private static class ConditionsImpl extends BiomeMatcher {

		protected BiomeInfo current;
		protected final Expression exp;

		public ConditionsImpl(@Nonnull final BiomeConfig config) {
			this.exp = new Expression(config.conditions);

			// Scan the BiomeDictionary adding the the types
			try {
				final Field accessor = ReflectionHelper.findField(BiomeDictionary.Type.class, "byName");
				if (accessor != null) {
					@SuppressWarnings("unchecked")
					final Map<String, BiomeDictionary.Type> stuff = (Map<String, BiomeDictionary.Type>) accessor.get(null);
					for (final Entry<String, Type> e : stuff.entrySet()) {
						this.exp.addVariable(new Variant("biome.is" + e.getKey()) {

							@Override
							public int compareTo(Variant o) {
								return Booleans.compare(this.asBoolean(), o.asBoolean());
							}

							@Override
							public float asNumber() {
								return this.asBoolean() ? 1 : 0;
							}

							@Override
							public String asString() {
								return Boolean.toString(this.asBoolean());
							}

							@Override
							public boolean asBoolean() {
								return ConditionsImpl.this.current.isBiomeType(e.getValue());
							}

							@Override
							public Variant add(Variant term) {
								return new BooleanValue(this.asBoolean() || term.asBoolean());
							}
						});
					}
				}

			} catch (final Throwable t) {
				throw new RuntimeException("Cannot locate BiomeDictionary.Type table!");
			}
			
			// Compile it
			this.exp.getRPN();

		}

		@Override
		public boolean match(@Nonnull final BiomeInfo info) {
			this.current = info;
			return this.exp.eval().asBoolean();
		}

	}
}
