package net.nashlegend.sourcewall.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import net.nashlegend.sourcewall.model.AceModel;
import net.nashlegend.sourcewall.model.Article;
import net.nashlegend.sourcewall.model.UComment;
import net.nashlegend.sourcewall.view.ArticleView;
import net.nashlegend.sourcewall.view.MediumListItemView;

/**
 * Created by NashLegend on 2014/9/18 0018
 */
public class ArticleDetailAdapter extends AceAdapter<AceModel> {

    private static final int Type_Article = 0;
    private static final int Type_Comment = 1;

    public ArticleDetailAdapter(Context context) {
        super(context);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? Type_Article : Type_Comment;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int tp = getItemViewType(position);
        if (convertView == null) {
            if (tp == Type_Article) {
                convertView = new ArticleView(getContext());
            } else {
                convertView = new MediumListItemView(getContext());
            }
        }
        if (convertView instanceof ArticleView) {
            ((ArticleView) convertView).setData((Article) list.get(position));
            ((ArticleView) convertView).setAdapter(this);
        } else {
            ((MediumListItemView) convertView).setData((UComment) list.get(position));
        }
        return convertView;
    }
}
