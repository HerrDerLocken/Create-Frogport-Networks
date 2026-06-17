package com.herrderlocken.frogportnetworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * NetworkCableBlock — physische Verbindung zwischen Netzwerkgeräten.
 *
 * Funktioniert ähnlich wie Redstone: verbindet sich automatisch mit
 * benachbarten Netzwerkgeräten (Router, NAS, Terminal, andere Kabel).
 *
 * Verwendet BooleanProperties für jede Richtung (NORTH, SOUTH, etc.),
 * damit das Blockmodel sich dynamisch anpasst — ein Kabel das nur
 * nach Norden und Süden verbunden ist sieht anders aus als eins
 * das in alle 4 Richtungen geht.
 *
 * Für Phase 1 halten wir es einfach: normaler Block ohne Richtungs-States.
 * Die Verbindungslogik kommt in Phase 2 mit Custom BakedModel.
 */
public class NetworkCableBlock extends Block {

    public NetworkCableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }



    /**
     * Hilfsmethode: Prüft ob ein benachbarter Block ein Netzwerkgerät ist.
     * Wird später für automatische Verbindung und Netzwerk-Discovery genutzt.
     */
    public static boolean isNetworkDevice(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof NetworkCableBlock
                || state.getBlock() instanceof RouterBlock
                || state.getBlock() instanceof NASBlock
                || state.getBlock() instanceof TerminalBlock;
    }
}
