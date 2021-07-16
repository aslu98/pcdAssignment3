package part2.rmiV1.puzzle;

import java.rmi.RemoteException;
import java.util.Optional;

public class SelectionManager {

	private Optional<Tile> selectedTile = Optional.empty();

	public void selectTile(final Tile tile, final Listener listener) throws RemoteException {
		
		if(selectedTile.isPresent()) {
			swap(selectedTile.get(), tile);
			selectedTile = Optional.empty();
			listener.onSwapPerformed();
		} else {
			selectedTile = Optional.of(tile);
		}
	}

	private void swap(final Tile t1, final Tile t2) {
		int pos = t1.getCurrentPosition();
		t1.setCurrentPosition(t2.getCurrentPosition());
		t2.setCurrentPosition(pos);
	}

	public Optional<Tile> getSelectedTile() {
		return selectedTile;
	}

	@FunctionalInterface
	interface Listener{
		void onSwapPerformed() throws RemoteException;
	}
}
