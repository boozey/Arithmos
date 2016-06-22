package com.nakedape.arithmos;

import android.net.Uri;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Created by Nathan on 5/29/2016.
 */
public class GameActivityItem implements Serializable{
    transient private static int serializationVersion = 1;
    // Activity list item types
    transient public static final int GOOGLE_PLAY_SAVED_GAME = 100;
    transient public static final int COMPLETED_LEVEL = 101;
    transient public static final int INCOMPLETE_LEVEL = 102;
    transient public static final int EMPTY_ITEM = 99;
    transient public static final int MATCH_INVITATION = 103;
    transient public static final int MATCH_THEIR_TURN = 104;
    transient public static final int MATCH_MY_TURN = 105;
    transient public static final int MATCH_COMPLETE = 106;

    transient public int itemType;

    public GameActivityItem(int itemType){
        this.itemType = itemType;
    }

    transient public String description;

    transient public String uniqueName;

    transient public String challengeName;

    transient public int challengeLevel;

    transient public Uri imageUri;

    transient public boolean canRematch;

    transient public String rematchId;

    transient public int matchResult;

    transient public long timeStamp;
    public long getLastModifiedTimeStamp() { return timeStamp; }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(serializationVersion);
        out.writeObject(uniqueName);
        out.writeObject(description);
        out.writeObject(imageUri);
        out.writeLong(timeStamp);
        out.writeInt(itemType);
        // Serialization version 1
        out.writeObject(challengeName);
        out.writeInt(challengeLevel);
    }
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        uniqueName = (String)in.readObject();
        description = (String)in.readObject();
        imageUri = (Uri)in.readObject();
        timeStamp = in.readLong();
        itemType = in.readInt();

        if (version >= 1) {
            challengeName = (String)in.readObject();
            challengeLevel = in.readInt();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GameActivityItem) {
            GameActivityItem item = (GameActivityItem) o;
            if (item.uniqueName != null)
                return item.uniqueName.equals(uniqueName);
            else if (item.challengeName != null)
                return item.challengeName.equals(challengeName) && item.challengeLevel == challengeLevel;
            else
                return item.timeStamp == timeStamp;
        } else if (o instanceof String) {
            String s = (String)o;
            return s.equals(uniqueName);
        }
        else return false;
    }
}
