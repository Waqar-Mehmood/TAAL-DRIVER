package com.android.taal_driver.historyRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.android.taal_driver.HistorySingleActivity;
import com.android.taal_driver.R;

import static com.android.taal_driver.AppConstants.RIDE_ID;

public class HistoryViewHolders extends RecyclerView.ViewHolder implements View.OnClickListener {

    public TextView mRideId;
    public TextView mTime;

    public HistoryViewHolders(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);

        mRideId = itemView.findViewById(R.id.ride_id);
        mTime = itemView.findViewById(R.id.time);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(v.getContext(), HistorySingleActivity.class);
        Bundle b = new Bundle();
        b.putString(RIDE_ID, mRideId.getText().toString());
        intent.putExtras(b);
        v.getContext().startActivity(intent);
    }
}