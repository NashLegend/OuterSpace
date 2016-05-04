package net.nashlegend.sourcewall.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.TextView;

import net.nashlegend.sourcewall.R;
import net.nashlegend.sourcewall.model.SubItem;

/**
 * Created by NashLegend on 2014/10/31 0031
 */
public class GroupItemView extends AceView<SubItem> {
    private SubItem item;
    private TextView textView;

    public GroupItemView(Context context) {
        super(context);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.layout_group_item_view, this);
        textView = (TextView) findViewById(R.id.text_subitem);
    }

    @Override
    public void setData(SubItem subItem) {
        item = subItem;
        textView.setText(item.getName());
    }

    @Override
    public SubItem getData() {
        return item;
    }
}
