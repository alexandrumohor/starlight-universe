package com.starlightuniverse.auth;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Injects a Netty interceptor into every incoming Minecraft connection to
 * capture the UUID the CLIENT sends during the login handshake. Official
 * launcher clients send their real Mojang UUID. Cracked launchers
 * (TLauncher etc.) send an offline UUID derived from the name.
 *
 * Compare the captured UUID with the real Mojang UUID for that username
 * ({@link AuthManager#checkMojangPremium}): match → official launcher →
 * safe to auto-login; mismatch → cracked → require password.
 */
public class PremiumSessionVerifier {

    private final Map<String, UUID> clientUuids = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;

    public PremiumSessionVerifier(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("unchecked")
    public void register() {
        try {
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object serverConnection = minecraftServer.getClass().getMethod("getConnection").invoke(minecraftServer);

            List<ChannelFuture> channels = null;
            for (Field f : serverConnection.getClass().getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object value = f.get(serverConnection);
                    if (value instanceof List<?> list && !list.isEmpty()
                            && list.getFirst() instanceof ChannelFuture) {
                        channels = (List<ChannelFuture>) value;
                        break;
                    }
                }
            }

            if (channels == null || channels.isEmpty()) {
                plugin.getLogger().warning("[SU] Could not find server channels — premium verification disabled.");
                return;
            }

            for (ChannelFuture future : channels) {
                injectServerChannel(future.channel());
            }

            plugin.getLogger().info("[SU] Premium session verifier registered.");
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Failed to register premium session verifier: " + e.getMessage());
        }
    }

    private void injectServerChannel(Channel serverChannel) {
        String acceptorName = null;
        for (Map.Entry<String, ChannelHandler> entry : serverChannel.pipeline()) {
            if (entry.getValue().getClass().getSimpleName().equals("ServerBootstrapAcceptor")) {
                acceptorName = entry.getKey();
                break;
            }
        }

        ChannelInboundHandlerAdapter interceptor = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.fireChannelRead(msg);
                if (msg instanceof Channel childChannel) {
                    childChannel.eventLoop().execute(() -> {
                        try {
                            var pipe = childChannel.pipeline();
                            if (pipe.get("splitter") != null) {
                                pipe.addAfter("splitter",
                                        "starlight-uuid-capture",
                                        new LoginUuidCapture(clientUuids));
                            } else if (pipe.get("timeout") != null) {
                                pipe.addAfter("timeout",
                                        "starlight-uuid-capture",
                                        new LoginUuidCapture(clientUuids));
                            } else {
                                pipe.addFirst("starlight-uuid-capture",
                                        new LoginUuidCapture(clientUuids));
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
        };

        if (acceptorName != null) {
            serverChannel.pipeline().addBefore(acceptorName, "starlight-interceptor", interceptor);
        } else {
            serverChannel.pipeline().addFirst("starlight-interceptor", interceptor);
        }
    }

    @SuppressWarnings("unchecked")
    public void unregister() {
        try {
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            Object serverConnection = minecraftServer.getClass().getMethod("getConnection").invoke(minecraftServer);

            for (Field f : serverConnection.getClass().getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object value = f.get(serverConnection);
                    if (value instanceof List<?> list && !list.isEmpty()
                            && list.getFirst() instanceof ChannelFuture) {
                        for (Object item : list) {
                            Channel ch = ((ChannelFuture) item).channel();
                            if (ch.pipeline().get("starlight-interceptor") != null) {
                                ch.pipeline().remove("starlight-interceptor");
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Failed to unregister premium session verifier: " + e.getMessage());
        }
        clientUuids.clear();
    }

    /**
     * Retrieves and removes the UUID the client sent for this username.
     * Empty if the client never logged in via the intercepted pipeline.
     */
    public Optional<UUID> consumeClientUuid(String username) {
        return Optional.ofNullable(clientUuids.remove(username.toLowerCase()));
    }

    private static class LoginUuidCapture extends ChannelInboundHandlerAdapter {

        private final Map<String, UUID> clientUuids;
        private boolean loginState;

        LoginUuidCapture(Map<String, UUID> clientUuids) {
            this.clientUuids = clientUuids;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            boolean shouldRemove = false;

            if (msg instanceof ByteBuf buf && buf.isReadable()) {
                int savedIndex = buf.readerIndex();
                try {
                    int packetId = readVarInt(buf);

                    if (!loginState && packetId == 0x00) {
                        readVarInt(buf);
                        readString(buf);
                        buf.readUnsignedShort();
                        int nextState = readVarInt(buf);
                        if (nextState == 2) {
                            loginState = true;
                        } else {
                            shouldRemove = true;
                        }
                    } else if (loginState && packetId == 0x00) {
                        String name = readString(buf);
                        if (buf.readableBytes() >= 16) {
                            clientUuids.put(name.toLowerCase(),
                                    new UUID(buf.readLong(), buf.readLong()));
                        }
                        shouldRemove = true;
                    }
                } catch (Exception ignored) {
                }
                buf.readerIndex(savedIndex);
            }

            ctx.fireChannelRead(msg);

            if (shouldRemove) {
                try {
                    ctx.pipeline().remove(this);
                } catch (Exception ignored) {
                }
            }
        }

        private static int readVarInt(ByteBuf buf) {
            int value = 0;
            int pos = 0;
            byte b;
            do {
                b = buf.readByte();
                value |= (b & 0x7F) << pos;
                pos += 7;
                if (pos >= 32) throw new IllegalStateException("VarInt too large");
            } while ((b & 0x80) != 0);
            return value;
        }

        private static String readString(ByteBuf buf) {
            int len = readVarInt(buf);
            byte[] data = new byte[len];
            buf.readBytes(data);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
