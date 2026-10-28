package com.ancientempires.block.decor;

import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** A block state property with named values (AW's wood variants, totem parts, gravestone numbers) without an enum per block. */
public final class AwStringProperty extends Property<String>
{
    private final List<String> values;

    public AwStringProperty(String name, List<String> values)
    {
        super(name, String.class);
        this.values = List.copyOf(values);
    }

    @Override
    public Collection<String> getPossibleValues()
    {
        return this.values;
    }

    @Override
    public String getName(String value)
    {
        return value;
    }

    @Override
    public Optional<String> getValue(String name)
    {
        return this.values.contains(name) ? Optional.of(name) : Optional.empty();
    }

    @Override
    public boolean equals(Object other)
    {
        return this == other || other instanceof AwStringProperty property && super.equals(other) && this.values.equals(property.values);
    }

    @Override
    public int generateHashCode()
    {
        return 31 * super.generateHashCode() + this.values.hashCode();
    }
}
