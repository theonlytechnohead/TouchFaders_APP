package net.ddns.anderserver.touchfadersapp;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class ItemMoveCallback extends ItemTouchHelper.Callback {

    private final ItemTouchHelperContract adapter;

    public ItemMoveCallback(ItemTouchHelperContract adapter) {
        this.adapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        return makeMovementFlags(dragFlags, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        if (viewHolder.getItemViewType() != target.getItemViewType()) {
            return false;
        }
        adapter.onChannelMoved(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder instanceof ChannelStripRecyclerViewAdapter.ChannelStripViewHolder) {
                ChannelStripRecyclerViewAdapter.ChannelStripViewHolder channelStripViewHolder = (ChannelStripRecyclerViewAdapter.ChannelStripViewHolder) viewHolder;
                adapter.onChannelSelected(channelStripViewHolder);
            }
        }
        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        if (viewHolder instanceof ChannelStripRecyclerViewAdapter.ChannelStripViewHolder) {
            ChannelStripRecyclerViewAdapter.ChannelStripViewHolder channelStripViewHolder = (ChannelStripRecyclerViewAdapter.ChannelStripViewHolder) viewHolder;
            adapter.onChannelClear(channelStripViewHolder);
        }
    }

    public interface ItemTouchHelperContract {

        void onChannelMoved(int fromPosition, int toPosition);

        void onChannelSelected(ChannelStripRecyclerViewAdapter.ChannelStripViewHolder channelStripViewHolder);

        void onChannelClear(ChannelStripRecyclerViewAdapter.ChannelStripViewHolder channelStripViewHolder);

    }
}
