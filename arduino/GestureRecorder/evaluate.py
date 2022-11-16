import os
import random
import math
import seaborn
import matplotlib.pyplot as plt 

num_train_samples = 1
threshold = 0.25
dtw_window = 50

# thresholds: 0.15, 0.2, ...

def read_gesture(path):
    with open(path, "r") as file:
        lines = [line.rstrip() for line in file]
        gesture = [[float(value) for value in data.split(',')] for data in lines]
        return gesture

labels = ['circle_ccw', 'circle_cw', 'heart_cw', 'square_ccw', 'triangle_cw', 'junk']

paths = os.listdir('gestures')
circle_ccw = [('circle_ccw', read_gesture('gestures/' + path)) for path in paths if path.startswith('circle_ccw')]
circle_cw = [('circle_cw', read_gesture('gestures/' + path)) for path in paths if path.startswith('circle_cw')]
heart_cw = [('heart_cw', read_gesture('gestures/' + path)) for path in paths if path.startswith('heart_cw')]
square_ccw = [('square_ccw', read_gesture('gestures/' + path)) for path in paths if path.startswith('square_ccw')]
triangle_cw = [('triangle_cw', read_gesture('gestures/' + path)) for path in paths if path.startswith('triangle_cw')]
junk = [('junk', read_gesture('gestures/' + path)) for path in paths if path.startswith('junk')]

def fir_lowpass_first(a):
    q = 0.95 
    b = [a[0]]
    for i in range(1, len(a)):
        x = (1.0 - q) * a[i - 1][0] + q * a[i][0]
        y = (1.0 - q) * a[i - 1][1] + q * a[i][1]
        z = (1.0 - q) * a[i - 1][2] + q * a[i][2]
        b.append([x, y, z])
    return b

def calc_distance(a, b) -> float:
    ax = a[0]
    ay = a[1]
    az = a[2]
    bx = b[0]
    by = b[1]
    bz = b[2]
    dir = (ax * bx + ay * by + az * bz) / (normalize(ax, ay, az) * normalize(bx, by, bz) + 0.0000001)

    return (1.0 - 0.5 * dir) * normalize(ax - bx, ay - by, az - bz)

def normalize(x, y, z) -> float:
    return math.sqrt(x * x + y * y + z * z)

def calc_dtw(a, b) -> float:
    a = fir_lowpass_first(a)
    b = fir_lowpass_first(b)

    dtw = [[0.0 for _ in range(50)] for _ in range(50)]
    dtw[0][0] = calc_distance(a[0], b[0])

    for i in range(1, 50):
        dtw[i][0] = calc_distance(a[i], b[0]) + dtw[i - 1][0]
        dtw[0][i] = calc_distance(a[0], b[i]) + dtw[0][i - 1]
    
    for i in range(1, 50):
        for j in range(1, 50):
            dtw[i][j] = calc_distance(a[i], b[j]) + min(dtw[i - 1][j], dtw[i][j - 1], dtw[i - 1][j - 1])
    
    i = 49
    j = 49
    distance = [0.0 for _ in range(100)]
    length = 0

    while i > 0 and j > 0:
        if dtw[i - 1][j] <= dtw[i][j - 1] and dtw[i - 1][j] <= dtw[i - 1][j - 1] and (j - i) <= dtw_window:
            distance[length] = dtw[i][j] - dtw[i - 1][j]
            i -= 1
        elif dtw[i][j - 1] < dtw[i - 1][j - 1] and (i - j) <= dtw_window:
            distance[length] = dtw[i][j] - dtw[i][j - 1]
            j -= 1
        else:
            distance[length] = dtw[i][j] - dtw[i - 1][j - 1]
            i -= 1
            j -= 1
        length += 1
    
    while i > 0:
        distance[length] = dtw[i][0] - dtw[i - 1][0]
        i -= 1
        length += 1
    
    while j > 0:
        distance[length] = dtw[0][j] - dtw[0][j - 1]
        j -= 1
        length += 1

    distance[length] = dtw[0][0]
    length += 1

    mean = 0.0

    for i in range(length):
        mean += distance[i]
    
    mean = mean / float(length)

    return mean

confusion_matrix = {}
num_trails = {}

for true_label in labels:
    confusion_matrix[true_label] = {}
    num_trails[true_label] = 0.0

    for predicted_label in labels:
        confusion_matrix[true_label][predicted_label] = 0.0

for _ in range(25):
    random.shuffle(circle_ccw)
    random.shuffle(circle_cw)
    random.shuffle(heart_cw)
    random.shuffle(square_ccw)
    random.shuffle(triangle_cw)

    circle_ccw_train = circle_ccw[:num_train_samples]
    circle_ccw_test = circle_ccw[num_train_samples:]

    circle_cw_train = circle_cw[:num_train_samples]
    circle_cw_test = circle_cw[num_train_samples:]

    heart_cw_train = heart_cw[:num_train_samples]
    heart_cw_test = heart_cw[num_train_samples:]

    square_ccw_train = square_ccw[:num_train_samples]
    square_ccw_test = square_ccw[num_train_samples:]

    triangle_cw_train = triangle_cw[:num_train_samples]
    triangle_cw_test = triangle_cw[num_train_samples:]

    train = circle_ccw_train + circle_cw_train + heart_cw_train + square_ccw_train + triangle_cw_train
    test = circle_ccw_test + circle_cw_test + heart_cw_test + square_ccw_test + triangle_cw_test + junk

    for (predicted_label, gesture) in test:
        means = [(a, calc_dtw(gesture, tr)) for (a, tr) in train]
        means.sort(key=lambda x:x[1])

        true_label = means[0][0]
        mean = means[0][1]

        if mean > threshold:
            true_label = 'junk'

        confusion_matrix[true_label][predicted_label] += 1.0
        num_trails[predicted_label] += 1.0

cf_plot = [[0 for _ in labels] for _ in labels]

for (i, true_label) in enumerate(['circle_ccw', 'circle_cw', 'heart_cw', 'square_ccw', 'triangle_cw', 'junk']):
    for (j, predicted_label) in enumerate(['circle_ccw', 'circle_cw', 'heart_cw', 'square_ccw', 'triangle_cw', 'junk']):
        cf_plot[j][i] = confusion_matrix[true_label][predicted_label] / num_trails[predicted_label]


plt.tick_params(labeltop=True, labelbottom=False)
seaborn.heatmap(cf_plot, cmap='rocket_r', annot=True, vmin=0.0, vmax=1.0, xticklabels=labels, yticklabels=labels, cbar=False)
plt.show()
