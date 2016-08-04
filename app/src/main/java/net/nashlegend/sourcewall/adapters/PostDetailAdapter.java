package net.nashlegend.sourcewall.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import net.nashlegend.sourcewall.model.AceModel;
import net.nashlegend.sourcewall.model.Post;
import net.nashlegend.sourcewall.model.UComment;
import net.nashlegend.sourcewall.view.MediumListItemView;
import net.nashlegend.sourcewall.view.PostView;

/**
 * Created by NashLegend on 2014/9/18 0018
 */
public class PostDetailAdapter extends AceAdapter<AceModel> {
    private static final int Type_Post = 0;
    private static final int Type_Comment = 1;

    public PostDetailAdapter(Context context) {
        super(context);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? Type_Post : Type_Comment;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int tp = getItemViewType(position);
        if (convertView == null) {
            if (tp == Type_Post) {
                convertView = new PostView(getContext());
            } else {
                convertView = new MediumListItemView(getContext());
            }
        }
        if (convertView instanceof PostView) {
            ((PostView) convertView).setData((Post) list.get(position));
            ((PostView) convertView).setAdapter(this);
        } else {
            ((MediumListItemView) convertView).setData((UComment) list.get(position));
        }
        return convertView;
    }
}
