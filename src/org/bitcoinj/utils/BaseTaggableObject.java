package org.bitcoinj.utils;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;

/**
 * A simple implementation of {@link TaggableObject} that just uses a lazily created hashmap
 * that is synchronized on this objects Java monitor.
 */
public class BaseTaggableObject implements TaggableObject
{
    @Nullable protected Map<String, ByteString> tags;

    /** {@inheritDoc} */
    @Override
    @Nullable
    public synchronized ByteString maybeGetTag(String tag)
    {
        return (tags != null) ? tags.get(tag) : null;
    }

    /** {@inheritDoc} */
    @Override
    public ByteString getTag(String tag)
    {
        ByteString b = maybeGetTag(tag);
        if (b == null)
            throw new IllegalArgumentException("Unknown tag " + tag);
        return b;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void setTag(String tag, ByteString value)
    {
        checkNotNull(tag);
        checkNotNull(value);

        if (tags == null)
            tags = new HashMap<>();
        tags.put(tag, value);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized Map<String, ByteString> getTags()
    {
        return (tags != null) ? Maps.newHashMap(tags) : Maps.newHashMap();
    }
}
