package net.xalcon.torchmaster.common.logic.entityblocking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.village.VillageSiegeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.xalcon.torchmaster.Torchmaster;
import net.xalcon.torchmaster.TorchmasterConfig;
import net.xalcon.torchmaster.common.ModCaps;

@Mod.EventBusSubscriber(modid = Torchmaster.MODID)
public class EntityBlockingEventHandler
{
    private static boolean isIntentionalSpawn(MobSpawnType spawnType)
    {
        switch(spawnType)
        {
            case BREEDING:
            case DISPENSER:
            case BUCKET:
            case CONVERSION:
            case SPAWN_EGG:
            case TRIGGERED:
            case COMMAND:
            case EVENT:
                return true;
            case NATURAL:
            case CHUNK_GENERATION:
            case PATROL:
            case SPAWNER:
            case STRUCTURE:
            case MOB_SUMMONED:
            case REINFORCEMENT:
            case JOCKEY:
            default:
                return false;
        }
    }

    private static boolean isNaturalSpawn(MobSpawnType spawnType)
    {
        switch(spawnType)
        {
            case NATURAL:
            case CHUNK_GENERATION:
            case PATROL: // Patrol can be considered natural
            default:
                return true;
            case BREEDING:
            case CONVERSION:
            case BUCKET:
            case DISPENSER:
            case SPAWNER:
            case STRUCTURE:
            case MOB_SUMMONED:
            case JOCKEY:
            case REINFORCEMENT:
            case TRIGGERED:
            case SPAWN_EGG:
            case COMMAND:
            case EVENT:
                return false;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event)
    {
        boolean log = TorchmasterConfig.GENERAL.logSpawnChecks.get();
        if (log) Torchmaster.Log.debug("CheckSpawn - SpawnType: {}, EntityType: {}, Pos: {}/{}/{}", event.getSpawnType(), EntityType.getKey(event.getEntity().getType()), event.getX(), event.getY(), event.getZ());
        if(event.isSpawnCancelled()) return;

        // Check if the spawn was intentional (i.e. player invoked), we dont block those
        if(isIntentionalSpawn(event.getSpawnType()))
            return;

        if(!TorchmasterConfig.GENERAL.aggressiveSpawnChecks.get() && event.getResult() == Event.Result.ALLOW) return;
        if(TorchmasterConfig.GENERAL.blockOnlyNaturalSpawns.get())
        {
            if(!isNaturalSpawn(event.getSpawnType())) return;
        }

        var entity = event.getEntity();
        var world = entity.getCommandSenderWorld();

        var pos = new BlockPos((int)event.getX(), (int)event.getY(), (int)event.getZ());

        world.getCapability(ModCaps.TEB_REGISTRY).ifPresent(reg ->
        {
            if(reg.shouldBlockEntity(entity, pos))
            {
                event.setResult(Event.Result.DENY);
                event.setSpawnCancelled(true);
                event.setCanceled(true);
                if (log) Torchmaster.Log.debug("Blocking spawn of {}", EntityType.getKey(event.getEntity().getType()));
                //event.getEntity().addTag("torchmaster_removed_spawn");
            }
            else
            {
                if (log) Torchmaster.Log.debug("Allowed spawn of {}", EntityType.getKey(event.getEntity().getType()));
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onVillageSiegeEvent(VillageSiegeEvent event)
    {
        if(!TorchmasterConfig.GENERAL.blockVillageSieges.get()) return;
        boolean log = TorchmasterConfig.GENERAL.logSpawnChecks.get();
        if (log) Torchmaster.Log.debug("VillageSiegeEvent - Pos: {}", event.getAttemptedSpawnPos());
        if(!TorchmasterConfig.GENERAL.aggressiveSpawnChecks.get() && event.getResult() == Event.Result.ALLOW) return;

        var vec = event.getAttemptedSpawnPos();
        var pos = new BlockPos((int)vec.x, (int)vec.y, (int)vec.z);
        var level = event.getLevel();

        level.getCapability(ModCaps.TEB_REGISTRY).ifPresent(reg ->
        {
            if(reg.shouldBlockVillageSiege(pos))
            {
                event.setResult(Event.Result.DENY);
                if(event.isCancelable())
                    event.setCanceled(true);
                if (log) Torchmaster.Log.debug("Blocking village siege @ {}", pos);
            }
            else
            {
                if (log) Torchmaster.Log.debug("Allowed village siege @ {}", pos);
            }
        });

    }

    @SubscribeEvent
    public static void onWorldAttachCapabilityEvent(AttachCapabilitiesEvent<Level> event)
    {
        event.addCapability(new ResourceLocation(Torchmaster.MODID, "registry"), new LightsRegistryCapability());
    }

    @SubscribeEvent
    public static void onGlobalTick(TickEvent.ServerTickEvent event)
    {
        if(event.side == LogicalSide.CLIENT) return;
        if(event.phase == TickEvent.Phase.END)
        {
            if(Torchmaster.server == null) return;

            for(ServerLevel level : Torchmaster.server.getAllLevels())
            {
                level.getProfiler().push("torchmaster_" + level.dimension().registry());
                level.getCapability(ModCaps.TEB_REGISTRY).ifPresent(reg -> reg.onGlobalTick(level));
                level.getProfiler().pop();
            }
        }
    }
}
