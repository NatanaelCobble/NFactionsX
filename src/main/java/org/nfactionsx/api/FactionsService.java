package org.nfactionsx.api;

import java.util.*;

/** Read-only integration surface. Results are detached snapshots, safe to inspect from other threads. */
public interface FactionsService {
    record FactionView(String id,String name,String description,int members,int claims,double power,long bankCents) { }
    record TerritoryView(String factionId,String zone) { }
    Optional<FactionView> faction(UUID player);
    TerritoryView territory(String world,int blockX,int blockZ);
}
