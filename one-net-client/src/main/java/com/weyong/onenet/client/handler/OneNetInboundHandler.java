package com.weyong.onenet.client.handler;

import com.weyong.onenet.client.context.OneNetClientContext;
import com.weyong.onenet.client.session.ClientSession;
import com.weyong.onenet.client.session.ServerSession;
import com.weyong.onenet.dto.DataTransfer;
import com.weyong.zip.ByteZipUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

/**
 * Created by hao.li on 2017/4/13.
 */
@Slf4j
public class OneNetInboundHandler extends SimpleChannelInboundHandler<DataTransfer> {
    private ServerSession serverSession;

    public OneNetInboundHandler(ServerSession serverSession){
        this.serverSession = serverSession;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DataTransfer msg) {
            switch (msg.getOpType()){
                case  DataTransfer.OP_TYPE_HEART_BEAT :
                    serverSession.setLastHeartbeatTime(new Date());
                    break;
                case DataTransfer.OP_TYPE_CLOSE:
                    String oneNetName = msg.getContextName();
                    Long sessionId = msg.getSessionId();
                    if (StringUtils.isNotEmpty(oneNetName) && sessionId != null) {
                        OneNetClientContext context = serverSession.getOneNetClientContextMap().get(oneNetName);
                        context.getSessionMap().get(sessionId).closeFromOneNet();
                    }
                    break;
                case DataTransfer.OP_TYPE_DATA:
                    if (StringUtils.isNotEmpty( msg.getContextName()) &&  msg.getSessionId() != null) {
                        OneNetClientContext context = serverSession.getOneNetClientContextMap().get( msg.getContextName());
                        ClientSession clientSession = context.
                                getSessionMap().computeIfAbsent(msg.getSessionId(), (id) -> {
                            ClientSession newClientSession = new ClientSession(id, ctx.channel(), context, null);
                            try {
                                newClientSession.setLocalChannel(context.getContextLocalChannel(newClientSession));
                                return newClientSession;
                            }catch (Exception ex) {
                                ctx.channel().writeAndFlush(
                                        new DataTransfer(
                                                newClientSession.getContextName(),
                                                id,DataTransfer.OP_TYPE_CLOSE));
                            }
                            return null;
                        });
                            clientSession.getLocalChannel().writeAndFlush(ByteZipUtil.unGzip(msg.getData()));

                        log.info("OneNet data to local");
                    }
                    break;

            }
    }
}
