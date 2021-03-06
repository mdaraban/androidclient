/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service.msgcenter.group;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.sm.StreamManagementException;

import android.net.Uri;

import org.kontalk.Log;
import org.kontalk.client.GroupExtension;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.client.XMPPTCPConnection;
import org.kontalk.provider.MyMessages;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.msgcenter.GroupCommandAckListener;
import org.kontalk.service.msgcenter.MessageCenterService;


public class KontalkGroupController implements GroupController<Message> {
    private static final String TAG = KontalkGroupController.class.getSimpleName();

    /** Group type identifier. */
    public static final String GROUP_TYPE = "kontalk";

    private final XMPPTCPConnection mConnection;
    private final MessageCenterService mInstance;

    public KontalkGroupController(XMPPConnection connection, MessageCenterService instance) {
        mConnection = (XMPPTCPConnection) connection;
        mInstance = instance;
    }

    @Override
    public String getGroupType() {
        return GROUP_TYPE;
    }

    @Override
    public Message beforeEncryption(GroupCommand command, Stanza packet) {
        String groupJid = command.getGroupJid();
        KontalkGroupManager.KontalkGroup group = KontalkGroupManager.getInstanceFor(mConnection)
            .getGroup(groupJid);

        if (packet == null)
            packet = new Message();

        if (command instanceof CreateGroupCommand) {
            KontalkCreateGroupCommand create = (KontalkCreateGroupCommand) command;
            group.create(create.getSubject(), create.getMembers(), packet);
        }
        else if (command instanceof SetSubjectCommand) {
            KontalkSetSubjectCommand setSubject = (KontalkSetSubjectCommand) command;
            group.setSubject(setSubject.getSubject(), packet);
        }
        else if (command instanceof AddRemoveMembersCommand) {
            KontalkAddRemoveMembersCommand addRemove = (KontalkAddRemoveMembersCommand) command;
            group.addRemoveMembers(addRemove.getSubject(), addRemove.getMembers(),
                addRemove.getAddedMembers(), addRemove.getRemovedMembers(), packet);
        }
        else if (command instanceof PartCommand) {
            group.leave(packet);
        }
        else if (command instanceof InfoCommand) {
            group.groupInfo(packet);
        }

        return (Message) packet;
    }

    @Override
    public Message afterEncryption(GroupCommand command, Stanza packet) {
        if (packet == null)
            throw new IllegalArgumentException("packet must be provided");
        if (!(command instanceof KontalkGroupCommand))
            throw new IllegalArgumentException("invalid command");

        String groupJid = command.getGroupJid();
        KontalkGroupManager.KontalkGroup group = KontalkGroupManager.getInstanceFor(mConnection)
            .getGroup(groupJid);

        if (command instanceof PartCommand) {
            try {
                String id = packet.getStanzaId();
                long msgId = ((PartCommand) command).getDatabaseId();

                // delete the command afterwards (only for part commands)
                Uri msgUri = (msgId > 0) ? MyMessages.Messages.getUri(msgId) : null;
                // wait for confirmation
                mConnection.addStanzaIdAcknowledgedListener(id,
                    new GroupCommandAckListener(mInstance, group,
                        GroupExtension.from(packet), msgUri));
            }
            catch (StreamManagementException.StreamManagementNotEnabledException e) {
                Log.e(TAG, "server does not support stream management?!?");
                // weird situation, report it
                ReportingManager.logException(e);
            }
        }
        else if (command instanceof AddRemoveMembersCommand) {
            try {
                String id = packet.getStanzaId();

                // wait for confirmation
                mConnection.addStanzaIdAcknowledgedListener(id,
                    new GroupCommandAckListener(mInstance, group,
                        GroupExtension.from(packet), null));
            }
            catch (StreamManagementException.StreamManagementNotEnabledException e) {
                Log.e(TAG, "server does not support stream management?!?");
                // weird situation, report it
                ReportingManager.logException(e);
            }
        }

        KontalkGroupCommand cmd = (KontalkGroupCommand) command;
        group.addRouteExtension(cmd.getMembers(), packet);
        return (Message) packet;
    }

    @Override
    public CreateGroupCommand createGroup() {
        return new KontalkCreateGroupCommand();
    }

    @Override
    public SetSubjectCommand setSubject() {
        return new KontalkSetSubjectCommand();
    }

    @Override
    public PartCommand part() {
        return new KontalkPartCommand();
    }

    public AddRemoveMembersCommand addRemoveMembers() {
        return new KontalkAddRemoveMembersCommand();
    }

    @Override
    public InfoCommand info() {
        return new KontalkGroupInfoCommand();
    }

}
