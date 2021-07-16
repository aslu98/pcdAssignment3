package part2.rmiV1.puzzle;
import part2.rmiV1.Player;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("serial")
public class PuzzleBoard extends JFrame {
	
	private final int rows, columns;
    private final JPanel board;
    private List<Tile> tiles = new ArrayList<>();
    private final Player myPlayer;
	
	private SelectionManager selectionManager = new SelectionManager();

    public PuzzleBoard(final int rows, final int columns, final String imagePath, final Player myPlayer) {
    	this.rows = rows;
		this.columns = columns;
		this.myPlayer = myPlayer;

    	setTitle("Puzzle");
        setResizable(false);

        board = new JPanel();
        board.setBorder(BorderFactory.createLineBorder(Color.gray));
        board.setLayout(new GridLayout(rows, columns, 0, 0));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().add(board, BorderLayout.CENTER);

        createTiles(imagePath);
        paintPuzzle();
    }


    private void createTiles(final String imagePath) {
		final BufferedImage image;

        try {
            image = ImageIO.read(new File(imagePath));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not load image", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final int imageWidth = image.getWidth(null);
        final int imageHeight = image.getHeight(null);

        int position = 0;

        final List<Integer> randomPositions = new ArrayList<>();
        IntStream.range(0, rows*columns).forEach(item -> { randomPositions.add(item); });
        Collections.shuffle(randomPositions);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
            	final Image imagePortion = createImage(new FilteredImageSource(image.getSource(),
                        new CropImageFilter(j * imageWidth / columns,
                        					i * imageHeight / rows,
                        					(imageWidth / columns),
                        					imageHeight / rows)));

                tiles.add(new Tile(imagePortion, position, randomPositions.get(position)));
                position++;
            }
        }
	}

    private void paintPuzzle() {
        Optional<Tile> selectedTile = selectionManager.getSelectedTile();
    	board.removeAll();

    	Collections.sort(tiles);

    	tiles.forEach(tile -> {
    		final TileButton btn = new TileButton(tile);
            board.add(btn);
            btn.setBorder(BorderFactory.createLineBorder(selectedTile.isPresent() && tile == selectedTile.get() ? Color.red : Color.gray));
            btn.addActionListener(actionListener -> {
                try {
                    selectionManager.selectTile(tile, () -> {
                        paintPuzzle();
                        checkSolution();
                        this.myPlayer.updateFromBoard(this.getCurrentPositions());
                    });
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
    	});
    	
    	pack();
        setLocationRelativeTo(null);
    }

    private void checkSolution() throws RemoteException {
    	if(tiles.stream().allMatch(Tile::isInRightPlace)) {
    		JOptionPane.showMessageDialog(this, "Puzzle Completed!", "", JOptionPane.INFORMATION_MESSAGE);
            this.myPlayer.updateFromBoard(this.getCurrentPositions());
    		System.exit(0);
    	}
    }

    public List<Integer> getCurrentPositions() {
        return this.tiles.stream()
                .sorted((t1, t2) -> t1.getOriginalPosition() < t2.getOriginalPosition() ? -1 : (t1.getOriginalPosition() == t2.getOriginalPosition() ? 0 : 1))
                .map(tile -> tile.getCurrentPosition())
                .collect(Collectors.toList());
    }

    public void setCurrentPositions(final List<Integer> positions) throws RemoteException {
        for (int i=0; i<positions.size(); i++){
            int finalI = i;
            this.tiles.stream().filter(tile -> tile.getOriginalPosition() == finalI).findAny().get().setCurrentPosition(positions.get(i));
        }
        paintPuzzle();
        checkSolution();
    }
}
