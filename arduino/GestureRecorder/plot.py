import matplotlib.pyplot as plt

s = [0.93, 0.936, 0.942, 0.946, 0.954, 0.948, 0.946, 0.942, 0.962, 0.96, 0.944, 0.932, 0.946, 0.93, 0.936]

plt.xlabel('Coefficient α')
plt.ylabel('Accuracy')
plt.xticks([0, 2, 4, 6, 8, 10, 12, 14], [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0])
plt.plot(s, label = 'classify gesture')

plt.show()
