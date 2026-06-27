package com.nadirkhoulali.ucs.claim;

import com.nadirkhoulali.ucs.core.model.ChunkKey;
import com.nadirkhoulali.ucs.core.model.Claim;
import com.nadirkhoulali.ucs.core.model.ClaimChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClaimShape {
    private ClaimShape() {
    }

    public static boolean isConnected(Set<ClaimChunk> chunks) {
        return connectedComponents(chunks).size() == 1;
    }

    public static List<Set<ClaimChunk>> connectedComponents(Set<ClaimChunk> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        if (chunks.isEmpty()) {
            return List.of();
        }

        Set<ChunkKey> remaining = chunks.stream().map(ClaimChunk::key).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Set<ClaimChunk>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            ChunkKey start = remaining.iterator().next();
            Set<ChunkKey> componentKeys = visitComponent(start, remaining);
            components.add(componentKeys.stream().map(ClaimChunk::new).collect(Collectors.toUnmodifiableSet()));
        }
        return List.copyOf(components);
    }

    public static boolean touches(Claim claim, ChunkKey chunk) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(chunk, "chunk");
        return claim.chunks().stream().map(ClaimChunk::key).anyMatch(existing -> adjacent(existing, chunk));
    }

    public static boolean touches(Claim first, Claim second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first == second || !first.chunks().iterator().next().key().dimension().equals(second.chunks().iterator().next().key().dimension())) {
            return false;
        }
        return first.chunks().stream()
                .map(ClaimChunk::key)
                .anyMatch(firstChunk -> second.chunks().stream().map(ClaimChunk::key).anyMatch(secondChunk -> adjacent(firstChunk, secondChunk)));
    }

    public static Set<ChunkKey> keys(Set<ClaimChunk> chunks) {
        return chunks.stream().map(ClaimChunk::key).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<ChunkKey> visitComponent(ChunkKey start, Set<ChunkKey> remaining) {
        Set<ChunkKey> component = new LinkedHashSet<>();
        Queue<ChunkKey> queue = new ArrayDeque<>();
        queue.add(start);
        remaining.remove(start);

        while (!queue.isEmpty()) {
            ChunkKey current = queue.remove();
            component.add(current);
            for (ChunkKey neighbor : neighbors(current)) {
                if (remaining.remove(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return component;
    }

    private static boolean adjacent(ChunkKey first, ChunkKey second) {
        if (!first.dimension().equals(second.dimension())) {
            return false;
        }
        int dx = Math.abs(first.x() - second.x());
        int dz = Math.abs(first.z() - second.z());
        return dx + dz == 1;
    }

    private static Set<ChunkKey> neighbors(ChunkKey key) {
        Set<ChunkKey> neighbors = new HashSet<>();
        neighbors.add(new ChunkKey(key.dimension(), key.x() + 1, key.z()));
        neighbors.add(new ChunkKey(key.dimension(), key.x() - 1, key.z()));
        neighbors.add(new ChunkKey(key.dimension(), key.x(), key.z() + 1));
        neighbors.add(new ChunkKey(key.dimension(), key.x(), key.z() - 1));
        return neighbors;
    }
}
