package com.iot.backend.websocket;

import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.CopyOnWriteArraySet;

@ServerEndpoint("/ws/monitor")
@Component
public class WebSocketServer {

    // 存放所有连接上来的前端浏览器客户端
    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("🔗 有新的监控大屏接入，当前在线人数: " + sessions.size());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("❌ 监控大屏断开，当前在线人数: " + sessions.size());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("⚠️ WebSocket 发生错误");
        error.printStackTrace();
    }

    // 🌟 核心方法：提供给网关引擎调用，用来向所有大屏广播数据
    public static void sendInfo(String message) {
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}